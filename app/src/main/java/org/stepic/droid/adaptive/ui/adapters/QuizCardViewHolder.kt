package org.stepic.droid.adaptive.ui.adapters

import android.support.annotation.StringRes
import android.support.design.widget.Snackbar
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.android.synthetic.main.adaptive_quiz_card_view.view.*
import org.stepic.droid.R
import org.stepic.droid.adaptive.ui.animations.CardAnimations
import org.stepic.droid.adaptive.ui.custom.container.ContainerView
import org.stepic.droid.analytic.Analytic
import org.stepic.droid.core.ScreenManager
import org.stepic.droid.core.presenters.CardPresenter
import org.stepic.droid.core.presenters.contracts.CardView
import org.stepic.droid.model.Submission
import org.stepic.droid.ui.adapters.StepikRadioGroupAdapter

class QuizCardViewHolder(
        private val root: View,
        analytic: Analytic,
        private val screenManager: ScreenManager
): ContainerView.ViewHolder(root), CardView {
    private val curtain = root.curtain
    private val answersProgress = root.answersProgress
    private val titleView = root.title
    private val question = root.question

    private val nextButton = root.next
    private val correctSign = root.correct
    private val wrongSign = root.wrong
    private val wrongButton = root.wrongRetry
    private val hint = root.hint

    private val actionButton = root.submit

    private val scrollContainer = root.scroll
    private val container = root.container

    private val choiceAdapter = StepikRadioGroupAdapter(root.answers, analytic)

    init {
        choiceAdapter.actionButton = actionButton

        question.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) = onCardLoaded()
        }
        question.setOnWebViewClickListener { screenManager.openImage(root.context, it) }
        question.setLayerType(View.LAYER_TYPE_SOFTWARE, null)

        nextButton.setOnClickListener { container.swipeDown() }
        actionButton.setOnClickListener { presenter?.createSubmission() }
        wrongButton.setOnClickListener {
            presenter?.let {
                it.retrySubmission()
                resetSupplementalActions()
            }
        }
        container.setNestedScroll(scrollContainer)
    }

    private var hasSubmission = false
    private var presenter: CardPresenter? = null

    fun bind(presenter: CardPresenter) {
        this.presenter = presenter
        presenter.attachView(this)
    }

    private fun onCardLoaded() {
        curtain.visibility = View.GONE
        if (presenter?.isLoading != true) answersProgress.visibility = View.GONE
    }

    override fun setTitle(title: String?) {
        title?.let { titleView.text = it }
    }

    override fun setQuestion(html: String?) {
        html?.let { question.setText(it) }
    }

    override fun setSubmission(submission: Submission, animate: Boolean) {
        resetSupplementalActions()
        when(submission.status) {
            Submission.Status.CORRECT -> {
                actionButton.visibility = View.GONE
                hasSubmission = true

                correctSign.visibility = View.VISIBLE
                nextButton.visibility = View.VISIBLE
                container.isEnabled = true

                if (submission.hint.isNotBlank()) {
                    hint.text = submission.hint
                    hint.visibility = View.VISIBLE
                }

                if (animate) {
                    scrollDown()
                }
            }

            Submission.Status.WRONG -> {
                wrongSign.visibility = View.VISIBLE
                hasSubmission = true

                wrongButton.visibility = View.VISIBLE
                actionButton.visibility = View.GONE

                container.isEnabled = true

                if (animate) {
                    CardAnimations.playWiggleAnimation(container)
                }
            }
        }
    }

    override fun onSubmissionConnectivityError() = onSubmissionError(R.string.no_connection)

    override fun onSubmissionRequestError() = onSubmissionError(R.string.request_error)

    private fun onSubmissionError(@StringRes errorMessage: Int) {
        if (root.parent != null) {
            Snackbar.make(root.parent as ViewGroup, errorMessage, Snackbar.LENGTH_SHORT).show()
        }
        container.isEnabled = true
        resetSupplementalActions()
    }

    override fun onSubmissionLoading() {
        resetSupplementalActions()
        container.isEnabled = false
        actionButton.visibility = View.GONE
        answersProgress.visibility = View.VISIBLE

        scrollDown()
    }

    override fun getRadioGroupAdapter() = choiceAdapter

    private fun scrollDown() {
        scrollContainer.post {
            scrollContainer.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun resetSupplementalActions() {
        nextButton.visibility = View.GONE
        correctSign.visibility = View.GONE
        wrongSign.visibility = View.GONE
        wrongButton.visibility = View.GONE
        answersProgress.visibility = View.GONE
        hint.visibility = View.GONE
        actionButton.visibility = View.VISIBLE
    }
}