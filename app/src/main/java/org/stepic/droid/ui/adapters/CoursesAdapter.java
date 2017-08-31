package org.stepic.droid.ui.adapters;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.annotation.ColorInt;
import android.support.annotation.DrawableRes;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.GlideDrawableImageViewTarget;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.stepic.droid.R;
import org.stepic.droid.analytic.Analytic;
import org.stepic.droid.base.App;
import org.stepic.droid.configuration.Config;
import org.stepic.droid.configuration.RemoteConfig;
import org.stepic.droid.core.ScreenManager;
import org.stepic.droid.core.presenters.ContinueCoursePresenter;
import org.stepic.droid.model.Course;
import org.stepic.droid.storage.operations.Table;
import org.stepic.droid.util.StepikLogicHelper;
import org.stepic.droid.util.resolvers.text.TextResolver;

import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import butterknife.BindColor;
import butterknife.BindView;
import butterknife.ButterKnife;

public class CoursesAdapter extends RecyclerView.Adapter<CoursesAdapter.CourseViewHolderBase> {

    @Inject
    Config config;

    @Inject
    TextResolver textResolver;

    @Inject
    ScreenManager screenManager;

    @Inject
    Analytic analytic;

    @Inject
    FirebaseRemoteConfig firebaseRemoteConfig;

    private Drawable coursePlaceholder;

    private LayoutInflater inflater;

    private Activity contextActivity;
    private final List<Course> courses;
    private final ContinueCoursePresenter continueCoursePresenter;
    private int footerViewType = 1;
    private int itemViewType = 2;
    private int NUMBER_OF_EXTRA_ITEMS = 1;
    private boolean isNeedShowFooter;
    private final String continueTitle;
    private final String joinTitle;
    private final boolean isContinueExperimentEnabled;

    public CoursesAdapter(Fragment fragment, List<Course> courses, @Nullable Table type, @NotNull ContinueCoursePresenter continueCoursePresenter) {
        contextActivity = fragment.getActivity();
        this.courses = courses;
        this.continueCoursePresenter = continueCoursePresenter;
        inflater = (LayoutInflater) contextActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        App.Companion.component().inject(this);
        coursePlaceholder = ContextCompat.getDrawable(fragment.getContext(), R.drawable.general_placeholder);
        isContinueExperimentEnabled = firebaseRemoteConfig.getBoolean(RemoteConfig.INSTANCE.getContinueCourseExperimentEnabledKey());
        if (isContinueExperimentEnabled) {
            continueTitle = contextActivity.getString(R.string.continue_course_title_experimental);
        } else {
            continueTitle = contextActivity.getString(R.string.continue_course_title);
        }
        joinTitle = contextActivity.getString(R.string.course_item_join);
    }

    @Override
    public CourseViewHolderBase onCreateViewHolder(ViewGroup parent, int viewType) {
        if (viewType == footerViewType) {
            View view = inflater.inflate(R.layout.loading_view, parent, false);
            return new FooterViewHolderItem(view);
        } else if (itemViewType == viewType) {
            View view = inflater.inflate(R.layout.new_course_item, parent, false);
            return new CourseViewHolderItem(view);
        } else {
            throw new IllegalStateException("Not valid item type");
        }
    }

    @Override
    public void onBindViewHolder(CourseViewHolderBase holder, int position) {
        holder.setDataOnView(position);
    }

    @Override
    public int getItemViewType(int position) {
        if (position == getItemCount() - 1) {
            return footerViewType;
        } else {
            return itemViewType;
        }
    }

    @Override
    public int getItemCount() {
        return courses.size() + NUMBER_OF_EXTRA_ITEMS;
    }

    private void onClickWidgetButton(int position, boolean enrolled) {
        if (position < 0 && position >= courses.size()) {
            //tbh, it is IllegalState
            return;
        }
        Course course = courses.get(position);
        if (enrolled) {
            analytic.reportEvent(Analytic.Interaction.CLICK_CONTINUE_COURSE);
            analytic.reportEvent(isContinueExperimentEnabled ? Analytic.ContinueExperiment.CONTINUE_NEW : Analytic.ContinueExperiment.CONTINUE_OLD);
            continueCoursePresenter.continueCourse(course); //provide position?
        } else {
            screenManager.showCourseDescription(contextActivity, course);
        }
    }

    private void onClickCourse(int position) {
        if (position >= courses.size() || position < 0) return;
        analytic.reportEvent(Analytic.Interaction.CLICK_COURSE);
        Course course = courses.get(position);
        if (course.getEnrollment() != 0) {
            analytic.reportEvent(isContinueExperimentEnabled ? Analytic.ContinueExperiment.COURSE_NEW : Analytic.ContinueExperiment.COURSE_OLD);
            screenManager.showSections(contextActivity, course);
        } else {
            screenManager.showCourseDescription(contextActivity, course);
        }
    }


    abstract static class CourseViewHolderBase extends RecyclerView.ViewHolder {

        public CourseViewHolderBase(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }

        abstract void setDataOnView(int position);
    }

    class CourseViewHolderItem extends CourseViewHolderBase {

        @BindView(R.id.courseItemName)
        TextView courseName;

        @BindView(R.id.courseItemImage)
        ImageView courseIcon;

        @BindView(R.id.courseWidgetButton)
        TextView courseWidgetButton;

        @BindView(R.id.courseItemLearnersCount)
        TextView learnersCount;

        @ColorInt
        @BindColor(R.color.new_accent_color)
        int continueColor;

        @ColorInt
        @BindColor(R.color.join_text_color)
        int joinColor;

        GlideDrawableImageViewTarget imageViewTarget;

        CourseViewHolderItem(final View itemView) {
            super(itemView);
            imageViewTarget = new GlideDrawableImageViewTarget(courseIcon);
            courseWidgetButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    int adapterPosition = getAdapterPosition();
                    if (!courses.isEmpty() && adapterPosition >= 0 && adapterPosition < courses.size()) {
                        CoursesAdapter.this.onClickWidgetButton(adapterPosition, isEnrolled(courses.get(adapterPosition)));
                    }
                }
            });
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    CoursesAdapter.this.onClickCourse(getAdapterPosition());
                }
            });

            itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    itemView.showContextMenu();
                    return true;
                }
            });
        }

        @Override
        void setDataOnView(int position) {
            final Course course = courses.get(position);

            courseName.setText(course.getTitle());
            Glide
                    .with(contextActivity)
                    .load(StepikLogicHelper.getPathForCourseOrEmpty(course, config))
                    .placeholder(coursePlaceholder)
                    .fitCenter()
                    .into(imageViewTarget);

            if (course.getLearnersCount() > 0) {
                learnersCount.setText(String.format(Locale.getDefault(), "%d", course.getLearnersCount()));
                learnersCount.setVisibility(View.VISIBLE);
            } else {
                learnersCount.setVisibility(View.GONE);
            }


            if (isEnrolled(course)) {
                showContinueButton();
            } else {
                showJoinButton();
            }
        }

        private boolean isEnrolled(Course course) {
            return course.getEnrollment() != 0 && course.isActive() && course.getLastStepId() != null;
        }

        private void showJoinButton() {
            showButton(joinTitle, joinColor, R.drawable.course_widget_join_background);
        }

        private void showContinueButton() {
            showButton(continueTitle, continueColor, R.drawable.course_widget_continue_background);
        }

        private void showButton(String title, @ColorInt int textColor, @DrawableRes int background) {
            courseWidgetButton.setText(title);
            courseWidgetButton.setTextColor(textColor);
            courseWidgetButton.setBackgroundResource(background);
        }

    }

    class FooterViewHolderItem extends CourseViewHolderBase {

        @BindView(R.id.loading_root)
        View loadingRoot;

        FooterViewHolderItem(View itemView) {
            super(itemView);
        }

        @Override
        void setDataOnView(int position) {
            loadingRoot.setVisibility(isNeedShowFooter ? View.VISIBLE : View.GONE);
        }
    }

    public void showLoadingFooter(boolean isNeedShow) {
        isNeedShowFooter = isNeedShow;
        try {
            notifyItemChanged(getItemCount() - 1);
        } catch (IllegalStateException ignored) {
            //if it is already notified
        }
    }

}
