package org.stepic.droid.ui.custom

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.support.annotation.StringRes
import android.support.v4.app.FragmentActivity
import android.support.v4.view.ViewCompat
import android.support.v7.widget.GridLayoutManager
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import kotlinx.android.synthetic.main.view_courses_carousel.view.*
import org.stepic.droid.R
import org.stepic.droid.analytic.AmplitudeAnalytic
import org.stepic.droid.analytic.Analytic
import org.stepic.droid.base.App
import org.stepic.droid.base.Client
import org.stepic.droid.core.ScreenManager
import org.stepic.droid.core.dropping.contract.DroppingListener
import org.stepic.droid.core.filters.contract.FiltersListener
import org.stepic.droid.core.joining.contract.JoiningListener
import org.stepic.droid.core.presenters.ContinueCoursePresenter
import org.stepic.droid.core.presenters.CourseCollectionPresenter
import org.stepic.droid.core.presenters.DroppingPresenter
import org.stepic.droid.core.presenters.PersistentCourseListPresenter
import org.stepic.droid.core.presenters.contracts.ContinueCourseView
import org.stepic.droid.core.presenters.contracts.CoursesView
import org.stepic.droid.core.presenters.contracts.DroppingView
import org.stepic.droid.model.*
import org.stepic.droid.storage.operations.Table
import org.stepic.droid.ui.adapters.CoursesAdapter
import org.stepic.droid.ui.decorators.LeftSpacesDecoration
import org.stepic.droid.ui.decorators.RightMarginForLastItems
import org.stepic.droid.ui.decorators.VerticalSpacesInGridDecoration
import org.stepic.droid.ui.dialogs.LoadingProgressDialogFragment
import org.stepic.droid.ui.util.CoursesSnapHelper
import org.stepic.droid.util.ColorUtil
import org.stepic.droid.util.ProgressHelper
import org.stepic.droid.util.StepikUtil
import org.stepic.droid.util.SuppressFBWarnings
import java.util.*
import javax.inject.Inject

@SuppressFBWarnings("RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE", justification = "Kotlin adds null check for lateinit properties, but Findbugs highlights it as redundant")
class CoursesCarouselView
@JvmOverloads
constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr),
        ContinueCourseView,
        CoursesView,
        DroppingView,
        JoiningListener,
        DroppingListener,
        FiltersListener {

    companion object {
        private const val DEFAULT_SCROLL_POSITION = -1
        private const val ROW_COUNT = 2

        private const val continueLoadingTag = "continueLoadingTag"
    }

    @Inject
    lateinit var courseListPresenter: PersistentCourseListPresenter

    @Inject
    lateinit var droppingPresenter: DroppingPresenter

    @Inject
    lateinit var continueCoursePresenter: ContinueCoursePresenter

    @Inject
    lateinit var droppingClient: Client<DroppingListener>

    @Inject
    lateinit var joiningListenerClient: Client<JoiningListener>

    @Inject
    lateinit var screenManager: ScreenManager

    @Inject
    lateinit var analytic: Analytic

    @Inject
    lateinit var courseCollectionPresenter: CourseCollectionPresenter

    @Inject
    lateinit var filterClient: Client<FiltersListener>

    private val courses = ArrayList<Course>()

    private var lastSavedScrollPosition: Int = DEFAULT_SCROLL_POSITION

    private var descriptionColors: CollectionDescriptionColors? = null

    private var _info: CoursesCarouselInfo? = null
        private set(value) {
            field = value
            if (value != null) {
                onInfoInitialized(value)
            }
        }
    private val info: CoursesCarouselInfo
        get() = _info ?: throw IllegalStateException("Info is not set")
    private var needExecuteOnInfoInitialized = false


    private var gridLayoutManager: GridLayoutManager? = null
    private val activity = context as FragmentActivity
    private val fragmentManager = activity.supportFragmentManager

    fun setCourseCarouselInfo(outerInfo: CoursesCarouselInfo) {
        _info = outerInfo
    }

    init {
        App
                .componentManager()
                .courseGeneralComponent()
                .courseListComponentBuilder()
                .build()
                .inject(this)

        val layoutInflater = LayoutInflater.from(context)
        layoutInflater.inflate(R.layout.view_courses_carousel, this, true)
        initCourseCarousel()
    }

    private fun onInfoInitialized(info: CoursesCarouselInfo) {
        needExecuteOnInfoInitialized = if (ViewCompat.isAttachedToWindow(this)) {
            initCourseCarouselWithInfo(info)

            courses.clear()
            downloadData()

            restoreState()

            false
        } else {
            true
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        continueCoursePresenter.attachView(this)
        courseListPresenter.attachView(this)
        droppingPresenter.attachView(this)
        droppingClient.subscribe(this)
        joiningListenerClient.subscribe(this)
        filterClient.subscribe(this)
        courseCollectionPresenter.attachView(this)

        if (needExecuteOnInfoInitialized || isCoursesNotLoadedYet()) {
            onInfoInitialized(info)
        }
    }

    private fun isCoursesNotLoadedYet() = courses.isEmpty() && _info != null

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        filterClient.unsubscribe(this)
        courseCollectionPresenter.detachView(this)
        joiningListenerClient.unsubscribe(this)
        droppingClient.unsubscribe(this)
        continueCoursePresenter.detachView(this)
        courseListPresenter.detachView(this)
        droppingPresenter.detachView(this)

        ProgressHelper.dismiss(fragmentManager, continueLoadingTag)
    }

    private fun initCourseCarousel() {
        coursesCarouselCount.visibility = View.GONE
        coursesViewAll.setOnClickListener {
            viewAll()
        }

        gridLayoutManager = GridLayoutManager(context, ROW_COUNT, GridLayoutManager.HORIZONTAL, false)
        coursesRecycler.layoutManager = gridLayoutManager
        val verticalSpaceBetweenItems = resources.getDimensionPixelSize(R.dimen.course_list_between_items_padding)
        val leftSpacePx = resources.getDimensionPixelSize(R.dimen.course_list_side_padding)

        coursesRecycler.addItemDecoration(VerticalSpacesInGridDecoration(verticalSpaceBetweenItems / 2, ROW_COUNT)) //warning: verticalSpaceBetweenItems/2 – workaround for some bug, decoration will set this param twice
        coursesRecycler.addItemDecoration(LeftSpacesDecoration(leftSpacePx))
        coursesRecycler.addItemDecoration(RightMarginForLastItems(resources.getDimensionPixelSize(R.dimen.home_right_recycler_padding_without_extra), ROW_COUNT))
        coursesRecycler.itemAnimator.changeDuration = 0
        val snapHelper = CoursesSnapHelper(ROW_COUNT)
        snapHelper.attachToRecyclerView(coursesRecycler)
    }

    private fun initCourseCarouselWithInfo(info: CoursesCarouselInfo) {
        coursesCarouselTitle.text = getCarouselTitle(info)
        coursesCarouselTitle.setTextColor(ColorUtil.getColorArgb(info.colorType.textColor))
        coursesCarouselRoot.setBackgroundColor(ColorUtil.getColorArgb(info.colorType.backgroundColorRes))
        coursesViewAll.setTextColor(ColorUtil.getColorArgb(info.colorType.viewAllColorRes, context))
        showDescription(info.description)

        val showMore = info.table == Table.enrolled
        coursesRecycler.adapter = CoursesAdapter(context as FragmentActivity, courses, continueCoursePresenter, droppingPresenter, false, showMore, info.colorType)
    }

    private fun showDescription(description: String) {
        if (description.isNotBlank()) {
            coursesCarouselDescription.setPlaceholderText(description)
            coursesCarouselDescription.visibility = View.VISIBLE
        } else {
            coursesCarouselDescription.visibility = View.GONE
        }
    }

    override fun onOpenStep(courseId: Long, section: Section, lessonId: Long, unitId: Long, stepPosition: Int) {
        ProgressHelper.dismiss(fragmentManager, continueLoadingTag)
        screenManager.continueCourse(activity, courseId, section, lessonId, unitId, stepPosition.toLong())
    }

    override fun onOpenAdaptiveCourse(course: Course) {
        ProgressHelper.dismiss(fragmentManager, continueLoadingTag)
        screenManager.continueAdaptiveCourse(activity, course)
    }

    override fun onAnyProblemWhileContinue(course: Course) {
        ProgressHelper.dismiss(fragmentManager, continueLoadingTag)
        screenManager.showSections(activity, course)
    }

    override fun onShowContinueCourseLoadingDialog() {
        val loadingProgressDialogFragment = LoadingProgressDialogFragment.newInstance()
        if (!loadingProgressDialogFragment.isAdded) {
            loadingProgressDialogFragment.show(fragmentManager, continueLoadingTag)
        }
    }

    override fun showLoading() {
        if (courses.isEmpty()) {
            coursesViewAll.visibility = View.GONE
            coursesRecycler.visibility = View.GONE
            coursesPlaceholder.visibility = View.GONE
            coursesCarouselCount.visibility = View.GONE
            coursesLoadingView.visibility = View.VISIBLE
        }
    }

    override fun showEmptyCourses() {
        @StringRes
        fun getEmptyStringRes(table: Table?): Int =
                when (table) {
                    Table.enrolled -> {
                        R.string.courses_carousel_my_courses_empty
                    }
                    Table.featured -> {
                        analytic.reportEvent(Analytic.Error.FEATURED_EMPTY)
                        R.string.empty_courses_popular
                    }
                    else -> {
                        analytic.reportEvent(Analytic.Error.COURSE_COLLECTION_EMPTY)
                        R.string.course_collection_empty
                    }
                }



        if (info.table == Table.enrolled) {
            analytic.reportEvent(Analytic.CoursesCarousel.EMPTY_ENROLLED_SHOWN)
        }
        showPlaceholder(getEmptyStringRes(info.table), {
            if (info.table == Table.enrolled) {
                analytic.reportEvent(Analytic.CoursesCarousel.EMPTY_ENROLLED_CLICK)
                screenManager.showCatalog(context)
            }
        })
    }

    override fun showConnectionProblem() {
        if (courses.isEmpty()) {
            analytic.reportEvent(Analytic.CoursesCarousel.NO_INTERNET_SHOWN)
            showPlaceholder(R.string.internet_problem, { _ ->
                analytic.reportEvent(Analytic.CoursesCarousel.NO_INTERNET_CLICK)
                if (StepikUtil.isInternetAvailable()) {
                    downloadData()
                }
            })
        }
    }

    override fun showCourses(courses: List<Course>) {
        coursesLoadingView.visibility = View.GONE
        coursesPlaceholder.visibility = View.GONE
        if (lastSavedScrollPosition != DEFAULT_SCROLL_POSITION) {
            coursesRecycler.scrollToPosition(lastSavedScrollPosition)
            lastSavedScrollPosition = DEFAULT_SCROLL_POSITION
        }
        showDescription(info.description)
        coursesRecycler.visibility = View.VISIBLE
        coursesViewAll.visibility = View.VISIBLE
        this.courses.clear()
        this.courses.addAll(courses)
        coursesRecycler.adapter.notifyDataSetChanged()
        updateOnCourseCountChanged()
    }

    private fun showPlaceholder(@StringRes stringRes: Int, listener: (view: View) -> Unit) {
        coursesViewAll.visibility = View.GONE
        coursesLoadingView.visibility = View.GONE
        coursesRecycler.visibility = View.GONE
        coursesCarouselDescription.visibility = View.GONE
        coursesPlaceholder.setPlaceholderText(stringRes)
        coursesPlaceholder.setOnClickListener(listener)
        coursesPlaceholder.visibility = View.VISIBLE
    }

    override fun onUserHasNotPermissionsToDrop() {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }


    override fun onSuccessDropCourse(course: Course) {
        val courseId = course.courseId
        analytic.reportEvent(Analytic.Course.DROP_COURSE_SUCCESSFUL, courseId.toString())
        analytic.reportAmplitudeEvent(AmplitudeAnalytic.Course.UNSUBSCRIBED, mapOf(AmplitudeAnalytic.Course.Params.COURSE to courseId))
        Toast.makeText(context, context.getString(R.string.you_dropped, course.title), Toast.LENGTH_LONG).show()
        val index = courses.indexOfFirst { it.courseId == course.courseId }

        if (index < 0) {
            //course is not in list
            return
        }

        if (info.table == Table.enrolled) {
            courses.removeAt(index)
            coursesRecycler.adapter.notifyItemRemoved(index)
            if (courses.size == ROW_COUNT) {
                // update 1st column for adjusting size
                coursesRecycler.adapter.notifyDataSetChanged()
            }
            updateOnCourseCountChanged()
        } else {
            courses[index].enrollment = 0
            coursesRecycler.adapter.notifyItemChanged(index)
        }

        if (courses.isEmpty()) {
            showEmptyCourses()
        }
    }

    override fun onFailDropCourse(course: Course) {
        val courseId = course.courseId
        analytic.reportEvent(Analytic.Course.DROP_COURSE_FAIL, courseId.toString())
        Toast.makeText(context, R.string.internet_problem, Toast.LENGTH_LONG).show()

    }

    private fun getCarouselTitle(info: CoursesCarouselInfo): String = info.title

    private fun restoreState() {
        if (info.table != null) {
            courseListPresenter.restoreState()
        } else if (info.table == null) {
            // no-op
        }
    }

    private fun downloadData() {
        info.table?.let {
            courseListPresenter.refreshData(it)
        }

        if (info.table == null) {
            info.courseIds?.let {
                courseCollectionPresenter.onShowCollections(it)
            }
        }
    }

    private fun viewAll() {
        screenManager.showCoursesList(activity, info, descriptionColors)
    }

    override fun onSuccessJoin(joinedCourse: Course) {
        val courseIndex = courses.indexOfFirst { it.courseId == joinedCourse.courseId }

        if (courseIndex >= 0) {
            courses[courseIndex].enrollment = joinedCourse.enrollment
            coursesRecycler.adapter.notifyItemChanged(courseIndex)
        } else if (info.table == Table.enrolled) {
            //insert at 0 index is more complex than just add, but order will be right
            if (courses.isEmpty()) {
                showCourses(mutableListOf(joinedCourse))
            } else {
                courses.add(0, joinedCourse)
                coursesRecycler.adapter.notifyDataSetChanged()
                updateOnCourseCountChanged()
            }
        }
    }

    private fun updateOnCourseCountChanged() {
        updateSpanCount()
        updateCourseCount()
    }

    private fun updateSpanCount() {
        if (courses.isEmpty()) {
            //do nothing
            return
        }

        val spanCount = Math.min(courses.size, ROW_COUNT)
        gridLayoutManager?.spanCount = spanCount
    }

    private fun updateCourseCount() {
        if (info.table == Table.featured || courses.isEmpty()) {
            coursesCarouselCount.visibility = View.GONE
        } else {
            coursesCarouselCount.visibility = View.VISIBLE
            coursesCarouselCount.text = resources.getQuantityString(R.plurals.course_count, courses.size, courses.size)
        }
    }

    public override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        val savedState = SavedState(superState)

        savedState.info = this._info
        savedState.scrollPosition = (coursesRecycler.layoutManager as GridLayoutManager).findFirstCompletelyVisibleItemPosition()
        return savedState
    }

    public override fun onRestoreInstanceState(state: Parcelable) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }

        super.onRestoreInstanceState(state.superState)
        this._info = state.info
        this.lastSavedScrollPosition = state.scrollPosition
    }

    private class SavedState : View.BaseSavedState {
        var info: CoursesCarouselInfo? = null
        var scrollPosition: Int = 0

        constructor(superState: Parcelable) : super(superState)

        private constructor(input: Parcel) : super(input) {
            this.info = input.readParcelable<CoursesCarouselInfo>(CoursesCarouselInfo::class.java.classLoader)
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeParcelable(this.info, flags)
        }

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(input: Parcel): SavedState = SavedState(input)

                override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
            }
        }
    }

    fun setDescriptionColors(collectionDescriptionColors: CollectionDescriptionColors?) {
        if (collectionDescriptionColors == null) {
            return
        }
        this.descriptionColors = collectionDescriptionColors
        with(coursesCarouselDescription) {
            setBackgroundResource(collectionDescriptionColors.backgroundRes)
            setTextColor(ColorUtil.getColorArgb(collectionDescriptionColors.textColorRes, context))
        }
    }

    override fun onFiltersChanged(filters: EnumSet<StepikFilter>) {
        if (info.table == Table.featured) {
            courseListPresenter.refreshData(Table.featured)
        }
    }

}
