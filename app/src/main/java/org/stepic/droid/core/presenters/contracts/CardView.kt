package org.stepic.droid.core.presenters.contracts

import org.stepik.android.model.Step
import org.stepik.android.model.Submission
import org.stepic.droid.ui.quiz.QuizDelegate

interface CardView {
    fun setSubmission(submission: Submission, animate: Boolean)
    fun onSubmissionConnectivityError()
    fun onSubmissionRequestError()
    fun onSubmissionLoading()

    fun setTitle(title: String?)
    fun setQuestion(html: String?)
    fun setStep(step: Step?)

    fun getQuizViewDelegate(): QuizDelegate
}