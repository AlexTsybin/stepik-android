package org.stepic.droid.core.presenters

import io.reactivex.Scheduler
import io.reactivex.disposables.CompositeDisposable
import org.stepic.droid.core.presenters.contracts.CatalogView
import org.stepic.droid.di.catalog.CatalogScope
import org.stepic.droid.di.qualifiers.BackgroundScheduler
import org.stepic.droid.di.qualifiers.MainScheduler
import org.stepic.droid.mappers.Mapper
import org.stepic.droid.model.CourseListItem
import org.stepic.droid.model.CoursesCarouselInfo
import org.stepic.droid.model.StepikFilter
import org.stepic.droid.web.Api
import java.util.*
import javax.inject.Inject

@CatalogScope
class CatalogPresenter
@Inject
constructor(
        private val api: Api,
        @BackgroundScheduler
        private val backgroundScheduler: Scheduler,
        @MainScheduler
        private val mainScheduler: Scheduler,
        private val mapper: Mapper<CourseListItem, CoursesCarouselInfo>
) : PresenterBase<CatalogView>() {

    private var disposableContainer: CompositeDisposable? = null

    fun onNeedLoadCatalog(filters: EnumSet<StepikFilter>) {
        if (filters.size > 1) {
            throw IllegalStateException("Filters are corrupted")
        }

        val lang = filters.first().language
        val disposable = api
                .getCourseLists(lang)
                .map {
                    mapper.map(it.courseLists)
                }
                .subscribeOn(backgroundScheduler)
                .observeOn(mainScheduler)
                .subscribe({
                    view?.showCarousels(it)
                }, {
                    view?.offlineMode()
                })
        disposableContainer?.add(disposable)
    }

    override fun attachView(view: CatalogView) {
        super.attachView(view)
        disposableContainer = CompositeDisposable()
    }

    override fun detachView(view: CatalogView) {
        super.detachView(view)
        disposableContainer?.dispose()
    }
}
