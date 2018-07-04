package org.stepic.droid.web;

import android.support.v4.app.FragmentActivity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.stepic.droid.adaptive.model.RatingItem;
import org.stepic.droid.model.Course;
import org.stepic.droid.model.NotificationCategory;
import org.stepic.droid.model.Reply;
import org.stepic.droid.model.Submission;
import org.stepic.droid.model.Tag;
import org.stepic.droid.adaptive.model.RecommendationReaction;
import org.stepic.droid.model.User;
import org.stepic.droid.model.comments.VoteValue;
import org.stepic.droid.social.ISocialType;
import org.stepic.droid.social.SocialManager;
import org.stepic.droid.web.model.adaptive.RatingRestoreResponse;
import org.stepic.droid.web.model.adaptive.RecommendationsResponse;

import java.util.List;

import io.reactivex.Completable;
import io.reactivex.Single;
import retrofit2.Call;


public interface Api {

    enum TokenType {
        social, loginPassword
    }

    Call<AuthenticationStepikResponse> authWithNativeCode(String code, SocialManager.SocialType type, @Nullable String email);

    Call<AuthenticationStepikResponse> authWithLoginPassword(String login, String password);

    Call<AuthenticationStepikResponse> authWithCode(String code);

    Call<RegistrationResponse> signUp(String firstName, String secondName, String email, String password);

    Single<CoursesMetaResponse> getEnrolledCourses(int page);

    Single<CoursesMetaResponse> getPopularCourses(int page);

    Call<StepicProfileResponse> getUserProfile();

    Call<UsersResponse> getUsers(long[] userIds);

    Single<List<User>> getUsersRx(long[] userIds);

    Call<Void> tryJoinCourse(@NotNull Course course);

    Call<SectionsMetaResponse> getSections(long[] sectionsIds);

    Single<SectionsMetaResponse> getSectionsRx(long[] sectionsIds);

    /**
     * Max number of  units defined in AppConstants
     */
    Call<UnitMetaResponse> getUnits(long[] units);

    Single<UnitMetaResponse> getUnitsRx(long[] units);

    Single<UnitMetaResponse> getUnits(long courseId, long lessonId);

    Call<LessonStepicResponse> getLessons(long[] lessons);

    Single<LessonStepicResponse> getLessonsRx(long[] lessons);

    Single<LessonStepicResponse> getLessons(long lessonId);

    Call<StepResponse> getSteps(long[] steps);

    Single<StepResponse> getStepsReactive(long[] steps);

    Single<StepResponse> getStepsByLessonId(long lessonId);

    @Nullable
    Call<Void> dropCourse(long courseId);

    Call<ProgressesResponse> getProgresses(String[] progresses);

    Single<ProgressesResponse> getProgressesReactive(String[] progresses);

    Call<AssignmentResponse> getAssignments(long[] assignmentsIds);

    Call<Void> postViewed(ViewAssignment stepAssignment);

    Completable postViewedReactive(ViewAssignment stepAssignment);

    void loginWithSocial(FragmentActivity activity, ISocialType type);

    Call<SearchResultResponse> getSearchResultsCourses(int page, String rawQuery);

    Single<QueriesResponse> getSearchQueries(String query);

    Call<CoursesMetaResponse> getCourses(int page, long[] ids);

    Single<CoursesMetaResponse> getCoursesReactive(int page, @NotNull long[] ids);

    Call<AttemptResponse> createNewAttempt(long stepId);

    Single<AttemptResponse> createNewAttemptReactive(long stepId);

    Call<SubmissionResponse> createNewSubmission(Reply reply, long attemptId);

    Completable createNewSubmissionReactive(Submission submission);

    Call<AttemptResponse> getExistingAttempts(long stepId);

    Single<AttemptResponse> getExistingAttemptsReactive(long stepId);

    Call<SubmissionResponse> getSubmissions(long attemptId);

    Single<SubmissionResponse> getSubmissionsReactive(long attemptId);

    Call<SubmissionResponse> getSubmissionForStep(long stepId);

    Call<Void> remindPassword(String email);

    Call<EmailAddressResponse> getEmailAddresses(long[] ids);

    Call<Void> sendFeedback(String email, String rawDescription);

    Call<DeviceResponse> getDevices();

    Call<DeviceResponse> getDevicesByRegistrationId(String token);

    Call<DeviceResponse> renewDeviceRegistration(long deviceId, String token);

    Call<DeviceResponse> registerDevice(String token);

    Call<CoursesMetaResponse> getCourse(long id);

    Call<Void> setReadStatusForNotification(long notificationId, boolean isRead);

    Completable setReadStatusForNotificationReactive(long notificationId, boolean isRead);

    Call<Void> removeDevice(long deviceId);

    Call<DiscussionProxyResponse> getDiscussionProxies(String discussionProxyId);

    Call<CommentsResponse> getCommentAnd20Replies(long commentId);

    Call<CommentsResponse> getCommentsByIds(long[] commentIds);

    Call<CommentsResponse> postComment(String text, long target /*for example, related step*/, @Nullable Long parent /*put if it is reply*/);

    Call<VoteResponse> makeVote(String voteId, @Nullable VoteValue voteValue);

    Call<CommentsResponse> deleteComment(long commentId);

    Call<CertificateResponse> getCertificates();

    Call<UnitMetaResponse> getUnitByLessonId(long lessonId);

    Call<NotificationResponse> getNotifications(NotificationCategory notificationCategory, int page);

    Call<Void> markAsReadAllType(@NotNull NotificationCategory notificationCategory);

    Single<NotificationStatusesResponse> getNotificationStatuses();

    Call<UserActivityResponse> getUserActivities(long userId);

    Single<UserActivityResponse> getUserActivitiesReactive(long userId);

    Call<LastStepResponse> getLastStepResponse(@NotNull String lastStepId);

    Single<CourseCollectionsResponse> getCourseCollections(String language);

    Single<CourseReviewResponse> getCourseReviews(int[] reviewSummaryIds);

    Single<TagResponse> getFeaturedTags();

    Single<SearchResultResponse> getSearchResultsOfTag(int page, @NotNull Tag tag);


    Single<RecommendationsResponse> getNextRecommendations(long courseId, int count);

    Completable createReaction(RecommendationReaction reaction);

    Single<List<RatingItem>> getRating(long courseId, int count, int days);

    Completable putRating(long courseId, long exp);

    Single<RatingRestoreResponse> restoreRating(long courseId);
}
