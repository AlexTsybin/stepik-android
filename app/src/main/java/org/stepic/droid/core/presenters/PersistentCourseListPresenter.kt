package org.stepic.droid.core.presenters

import android.support.annotation.WorkerThread
import org.stepic.droid.analytic.Analytic
import org.stepic.droid.concurrency.MainHandler
import org.stepic.droid.concurrency.SingleThreadExecutor
import org.stepic.droid.core.FilterApplicator
import org.stepic.droid.core.FirstCoursePoster
import org.stepic.droid.core.earlystreak.contract.EarlyStreakPoster
import org.stepic.droid.core.presenters.contracts.CoursesView
import org.stepic.droid.di.course_list.CourseListScope
import org.stepic.droid.features.deadlines.repository.DeadlinesRepository
import org.stepic.droid.model.Course
import org.stepic.droid.model.CourseReviewSummary
import org.stepic.droid.model.Progress
import org.stepic.droid.preferences.SharedPreferenceHelper
import org.stepic.droid.storage.operations.DatabaseFacade
import org.stepic.droid.storage.operations.Table
import org.stepic.droid.util.CourseUtil
import org.stepic.droid.util.DateTimeHelper
import org.stepic.droid.util.RWLocks
import org.stepic.droid.web.Api
import org.stepic.droid.web.CoursesMetaResponse
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

@CourseListScope
class PersistentCourseListPresenter
@Inject constructor(
        private val databaseFacade: DatabaseFacade,
        private val singleThreadExecutor: SingleThreadExecutor,
        private val mainHandler: MainHandler,
        private val api: Api,
        private val filterApplicator: FilterApplicator,
        private val sharedPreferenceHelper: SharedPreferenceHelper,
        private val earlyStreakPoster: EarlyStreakPoster,
        private val firstCoursePoster: FirstCoursePoster,

        private val deadlinesRepository: DeadlinesRepository,
        private val analytic: Analytic
) : PresenterBase<CoursesView>() {

    companion object {
        //if hasNextPage & < MIN_COURSES_ON_SCREEN -> load next page
        private const val MIN_COURSES_ON_SCREEN = 5
        private const val MAX_CURRENT_NUMBER_OF_TASKS = 2
        private const val SEVEN_DAYS_MILLIS = 7 * 24 * 60 * 60 * 1000L
        private const val MILLIS_IN_SECOND = 1000L
    }

    private val currentPage = AtomicInteger(1)
    private val hasNextPage = AtomicBoolean(true)
    private var currentNumberOfTasks: Int = 0 //only main thread
    private val isEmptyCourses = AtomicBoolean(false)

    fun restoreState() {
        if (isEmptyCourses.get() && !hasNextPage.get()) {
            view?.showEmptyCourses()
        }
    }

    /**
     * 1) Show from cache, if not empty (hide progress)
     * 2) Load from internet (if fail -> show)
     * 3) Save to db
     * 4) show from cache (all states)
     */
    fun downloadData(courseType: Table) {
        downloadData(courseType, isRefreshing = false)
    }

    private fun downloadData(courseType: Table, isRefreshing: Boolean, isLoadMore: Boolean = false) {
        if (currentNumberOfTasks >= MAX_CURRENT_NUMBER_OF_TASKS) {
            return
        }
        currentNumberOfTasks++
        singleThreadExecutor.execute {
            try {
                downloadDataPlain(isRefreshing, isLoadMore, courseType)
            } finally {
                mainHandler.post {
                    currentNumberOfTasks--
                }
            }
        }
    }

    @WorkerThread
    private fun downloadDataPlain(isRefreshing: Boolean, isLoadMore: Boolean, courseType: Table) {
        if (!isLoadMore) {
            mainHandler.post {
                view?.showLoading()
            }
            showFromDatabase(courseType)
        } else if (hasNextPage.get()) {
            mainHandler.post {
                view?.showLoading()
            }
        }

        while (hasNextPage.get()) {
            val coursesFromInternet: List<Course>? = try {
                if (courseType == Table.featured) {
                    val response = api.getPopularCourses(currentPage.get()).blockingGet()
                    handleMeta(response)
                    response.courses
                } else {
                    val allMyCourses = arrayListOf<Course>()
                    while (hasNextPage.get()) {
                        val originalResponse = api.getEnrolledCourses(currentPage.get()).blockingGet()
                        allMyCourses.addAll(originalResponse.courses)
                        handleMeta(originalResponse)
                    }
                    deadlinesRepository.syncDeadlines(allMyCourses).blockingAwait()
                    analytic.setCoursesCount(allMyCourses.size)
                    allMyCourses
                }
            } catch (ex: Exception) {
                null
            }?.distinctBy { it.courseId }

            if (coursesFromInternet == null) {
                mainHandler.post {
                    firstCoursePoster.postConnectionError()
                    view?.showConnectionProblem()
                }
                break
            }

            if (courseType == Table.enrolled) {
                val progressIds = coursesFromInternet.map { it.progress }.toTypedArray()
                val progresses: List<Progress>? = try {
                    api.getProgresses(progressIds).execute().body()?.progresses
                } catch (exception: Exception) {
                    //ok show without progresses
                    null
                }
                progresses?.forEach {
                    databaseFacade.addProgress(progress = it)
                }
            }

            val reviewSummaryIds = coursesFromInternet.map { it.reviewSummary }.toIntArray()
            val reviews: List<CourseReviewSummary>? = try {
                api.getCourseReviews(reviewSummaryIds).blockingGet().courseReviewSummaries
            } catch (exception: Exception) {
                //ok show without new ratings
                null
            }
            CourseUtil.applyReviewsToCourses(reviews, coursesFromInternet)

            try {
                //this lock need for not saving enrolled courses to database after user click logout
                RWLocks.ClearEnrollmentsLock.writeLock().lock()
                if (sharedPreferenceHelper.authResponseFromStore != null || courseType == Table.featured) {
                    if (isRefreshing) {
                        if (courseType == Table.featured && currentPage.get() == 2) {
                            databaseFacade.dropFeaturedCourses()
                        } else if (courseType == Table.enrolled) {
                            databaseFacade.dropEnrolledCourses()
                        }
                    }

                    coursesFromInternet.forEach {
                        databaseFacade.addCourse(it, courseType)
                    }
                }
            } finally {
                RWLocks.ClearEnrollmentsLock.writeLock().unlock()
            }

            val allCourses = databaseFacade.getAllCourses(courseType).filterNotNull().toMutableList()

            val coursesForShow: List<Course> = handleCoursesWithType(allCourses, courseType)
            if (coursesForShow.size < MIN_COURSES_ON_SCREEN && hasNextPage.get()) {
                //try to load next in loop
            } else {
                mainHandler.post {
                    postFirstCourse(courseType, coursesForShow)
                    if (coursesForShow.isEmpty()) {
                        isEmptyCourses.set(true)
                        view?.showEmptyCourses()
                    } else {
                        view?.showCourses(coursesForShow)
                    }
                }
                break
            }
        }
    }

    private fun handleMeta(response: CoursesMetaResponse) {
        hasNextPage.set(response.meta.has_next)
        if (hasNextPage.get()) {
            currentPage.set(response.meta.page + 1) // page for next loading
        }
    }

    @WorkerThread
    private fun showFromDatabase(courseType: Table) {
        val coursesBeforeLoading = databaseFacade.getAllCourses(courseType).filterNotNull()
        val coursesForShow = handleCoursesWithType(coursesBeforeLoading, courseType)

        if (coursesForShow.isNotEmpty()) {
            mainHandler.post {
                view?.showCourses(coursesForShow)
                postFirstCourse(courseType, coursesForShow)
            }
        }
    }

    private fun postFirstCourse(courseType: Table, coursesForShow: List<Course>) {
        if (courseType != Table.enrolled) {
            return
        }
        val course = coursesForShow.find {
            it.isActive && it.sections?.isNotEmpty() ?: false
        }
        firstCoursePoster.postFirstCourse(course)
    }

    private fun handleCoursesWithType(courses: List<Course>, courseType: Table?): List<Course> =
            when (courseType) {
                Table.enrolled -> {
                    val progressMap = getProgressesFromDb(courses)
                    CourseUtil.applyProgressesToCourses(progressMap, courses)
                    val sortedCourses = sortByLastAction(courses, progressMap)
                    postLastActive(sortedCourses.firstOrNull(), progressMap)
                    sortedCourses
                }
                Table.featured -> {
                    filterApplicator.filterCourses(courses)
                }
                null -> courses
            }

    private fun postLastActive(course: Course?, progressMap: Map<String?, Progress>) {

        val lastViewed = progressMap[course?.progress]?.lastViewed?.toLongOrNull()

        if (lastViewed != null && isViewedDuringLast7Days(lastViewed)) {
            mainHandler.post {
                earlyStreakPoster.showStreakSuggestion()
            }
        }
    }

    private fun isViewedDuringLast7Days(lastViewed: Long): Boolean =
            DateTimeHelper.isAfterNowUtc(lastViewed * MILLIS_IN_SECOND + SEVEN_DAYS_MILLIS)

    fun refreshData(courseType: Table) {
        if (currentNumberOfTasks >= MAX_CURRENT_NUMBER_OF_TASKS) {
            return
        }
        currentPage.set(1)
        hasNextPage.set(true)
        downloadData(courseType, isRefreshing = true)
    }

    @WorkerThread
    private fun sortByLastAction(courses: List<Course>, idProgressesMap: Map<String?, Progress>): MutableList<Course> {
        return courses.sortedWith(Comparator { course1, course2 ->
            val progress1: Progress? = idProgressesMap[course1.progress]
            val progress2: Progress? = idProgressesMap[course2.progress]

            val lastViewed1 = progress1?.lastViewed?.toLongOrNull()
            val lastViewed2 = progress2?.lastViewed?.toLongOrNull()

            if (lastViewed1 == null && lastViewed2 == null) {
                return@Comparator (course2.courseId - course1.courseId).toInt() // course2 - course1 (greater id is 1st)
            }

            if (lastViewed1 == null) {
                return@Comparator 1 // 1st after 2nd
            }

            if (lastViewed2 == null) {
                return@Comparator -1 //1st before 2nd. 2nd to end
            }

            return@Comparator (lastViewed2 - lastViewed1).toInt()
        }).toMutableList()
    }

    @WorkerThread
    private fun getProgressesFromDb(courses: List<Course>): Map<String?, Progress> {
        val progressIds = courses.mapNotNull {
            it.progress
        }
        return databaseFacade.getProgresses(progressIds).associateBy { it.id }
    }


    fun loadMore(courseType: Table) {
        downloadData(courseType, isRefreshing = false, isLoadMore = true)
    }
}
