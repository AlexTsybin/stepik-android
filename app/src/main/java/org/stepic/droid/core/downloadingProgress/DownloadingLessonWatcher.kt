package org.stepic.droid.core.downloadingProgress

import io.reactivex.Flowable
import org.stepic.droid.storage.operations.DatabaseFacade
import org.stepic.droid.util.RetryWithDelay

class DownloadingLessonWatcher
constructor(
        private val databaseFacade: DatabaseFacade,
        private val stepProgressPublisher: StepProgressPublisher
) : DownloadingWatcher {

    companion object {
        private const val RETRY_DELAY: Int = 300
    }

    override fun watch(id: Long): Flowable<Float> =
            Flowable
                    .fromCallable {
                        //it can be null
                        databaseFacade.getLessonById(id) ?: throw LessonIsNotCachedException()

                    }
                    .retryWhen(RetryWithDelay(RETRY_DELAY)) //retry if lessons are empty in database
                    .cache()
                    .map {
                        it.steps
                    }
                    .map {
                        it.toMutableList()
                    }
                    .reduce { accumulator: MutableList<Long>, item: MutableList<Long> ->
                        accumulator.addAll(item)
                        accumulator
                    }
                    .map {
                        it.toSet()
                    }
                    .cache()
                    .toFlowable()
                    .flatMap {
                        stepProgressPublisher.subscribe(it)
                    }

    class LessonIsNotCachedException : Exception("lesson is not in database yet")

}
