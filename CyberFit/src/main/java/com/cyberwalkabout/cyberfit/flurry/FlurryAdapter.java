package com.cyberwalkabout.cyberfit.flurry;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import com.cyberwalkabout.cyberfit.AppSettings;
import com.cyberwalkabout.cyberfit.model.v2.Exercise;
import com.cyberwalkabout.cyberfit.model.v2.ExerciseSession;
import com.cyberwalkabout.cyberfit.model.v2.Program;
import com.cyberwalkabout.cyberfit.model.v2.User;
import com.flurry.android.FlurryAgent;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;

/**
 * @author Andrii Kovalov, Uki D. Lucas
 */
public class FlurryAdapter {
    public static final String FLURRY_EVENT_EXERCISE_COMPLETED = "exercise_completed";
    public static final String FLURRY_EVENT_SOCIAL_NETWORK_LOGIN = "social_network_login";
    public static final String FLURRY_EVENT_PROGRAM_OPENED = "program_opened";
    public static final String FLURRY_EVENT_ADD_BODY_MEASUREMENT = "add_body_measurement";
    public static final String FLURRY_EVENT_ADD_EXERCISE_HISTORY_RECORD = "add_exercise_history_record";
    public static final String FLURRY_EVENT_DELETE_EXERCISE_HISTORY_RECORD = "delete_exercise_history_record";
    public static final String FLURRY_EVENT_SHARE_EXERCISE_HISTORY = "share_exercise_history";
    public static final String FLURRY_EVENT_UPDATE_PROFILE = "update_profile";
    public static final String FLURRY_EVENT_UPDATE_PROFILE_IMAGE = "update_profile_image";
    public static final String FLURRY_EVENT_INFO_OPENED = "info_opened";
    public static final String FLURRY_EVENT_ADD_SCHEDULE_ENTRY = "add_schedule_entry";
    public static final String FLURRY_EVENT_DELETE_SCHEDULE_ENTRY = "delete_schedule_entry";
    public static final String FLURRY_EVENT_SHARE_SCHEDULE = "share_schedule";
    public static final String FLURRY_EVENT_SET_USER_GOALS = "set_user_goals";
    public static final String FLURRY_EVENT_EXERCISE_LOG_OPENED = "exercise_log_opened";
    private static final String TAG = FlurryAdapter.class.getSimpleName();
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");
    private static final FlurryAdapter INSTANCE = new FlurryAdapter();

    static {
        TIME_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT+0"));
    }

    public String FLURRY_KEY; // in src/main/assets/secret.properties

    private FlurryAdapter() {

        Properties prop = new Properties();

        try {
            //TODO Uki: not finished: java.io.FileNotFoundException: cyberfit_prod.properties: open failed: ENOENT (No such file or directory)

            File file = new File("cyberfit_prod.properties");
            Log.d(TAG, "Attempting to read: " + file.getAbsolutePath());
            prop.load(new FileInputStream(file));
            Log.d(TAG, "Reading FLURRY_KEY: " + prop.getProperty("FLURRY_KEY"));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static FlurryAdapter getInstance() {
        return INSTANCE;
    }

    public void startSession(Context context) {
        FlurryAgent.onStartSession(context, FLURRY_KEY);
    }

    public void endSession(Context context) {
        FlurryAgent.onEndSession(context);
    }

    public void programOpened(Program program, boolean isSubscribed, int numberOfExercises) {
        if (program != null) {
            Map<String, String> args = createProgramArgs(program);
            args.put("is_subscribed", String.valueOf(isSubscribed));
            args.put("number_of_exercises", String.valueOf(numberOfExercises));

            Log.d(TAG, FLURRY_EVENT_PROGRAM_OPENED + " " + args);
            FlurryAgent.logEvent(FLURRY_EVENT_PROGRAM_OPENED, args);
        }
    }

    public void exerciseCompleted(Exercise exercise, ExerciseSession exerciseSession) {
        Map<String, String> args = createExerciseArgs(exercise);

        if (exerciseSession != null) {
            if (exerciseSession.hasRepetitions() && exerciseSession.getRepetitions() > 0) {
                args.put("repetitions", String.valueOf(exerciseSession.getRepetitions()));
            }
            if (exerciseSession.hasDistance() && exerciseSession.getDistance() > 0) {
                args.put("distance", String.format("%.1f", exerciseSession.getDistance()));
            }
            if (exerciseSession.hasTime() && exerciseSession.getTime() > 0) {
                args.put("time", TIME_FORMAT.format(new Date(exerciseSession.getTime())));
                args.put("time_seconds", String.valueOf(exerciseSession.getTime() / 1000));
            }
            if (exerciseSession.hasWeight() && exerciseSession.getWeight() > 0) {
                args.put("weight", String.format("%.1f", exerciseSession.getWeight()));
            }
        }

        Log.d(TAG, FLURRY_EVENT_EXERCISE_COMPLETED + " " + args);
        FlurryAgent.logEvent(FLURRY_EVENT_EXERCISE_COMPLETED, args);
    }


    public void exerciseLogOpened(int totalCompleted, int todayCompleted) {
        Map<String, String> args = new HashMap<>();
        args.put("number_of_exercises_total", String.valueOf(totalCompleted));
        args.put("number_of_exercises_today", String.valueOf(todayCompleted));

        Log.d(TAG, FLURRY_EVENT_EXERCISE_LOG_OPENED + " " + args);
        FlurryAgent.logEvent(FLURRY_EVENT_EXERCISE_LOG_OPENED, args);
    }

    public void deleteExerciseSession(long exerciseSessionId, String exerciseName) {
        Map<String, String> args = new HashMap<String, String>();
        args.put("exercise_id", String.valueOf(exerciseSessionId));
        args.put("exercise_name", exerciseName);
        FlurryAgent.logEvent(FLURRY_EVENT_DELETE_EXERCISE_HISTORY_RECORD, args);
    }

    public void addExerciseSession(String exerciseId, String exerciseName) {
        Map<String, String> args = new HashMap<String, String>();
        args.put("exercise_id", String.valueOf(exerciseId));
        args.put("exercise_name", exerciseName);
        FlurryAgent.logEvent(FLURRY_EVENT_ADD_EXERCISE_HISTORY_RECORD, args);
    }

    public void updateProfileImage() {
        FlurryAgent.logEvent(FLURRY_EVENT_UPDATE_PROFILE_IMAGE);
    }

    public void shareExerciseHistory(String shareApp, int numberOfExercises) {


        FlurryAgent.logEvent(FLURRY_EVENT_SHARE_EXERCISE_HISTORY);
    }

    public void infoOpened() {
        Log.d(TAG, FLURRY_EVENT_INFO_OPENED);
        FlurryAgent.logEvent(FLURRY_EVENT_INFO_OPENED);
    }

    public void infoOpened(String type) {
        Map<String, String> args = new HashMap<>();
        args.put("type", type);

        Log.d(TAG, FLURRY_EVENT_INFO_OPENED + " " + args);
        FlurryAgent.logEvent(FLURRY_EVENT_INFO_OPENED, args);
    }

    public void socialNetworkLogin(String loginAction) {
        Log.d(TAG, "socialNetworkLogin: " + loginAction);
        Map<String, String> args = new HashMap<String, String>();
        args.put("action_taken", loginAction);
        FlurryAgent.logEvent(FLURRY_EVENT_SOCIAL_NETWORK_LOGIN, args);
    }

    public void addBodyMeasurement(long userId, int type, String title) {
        Map<String, String> args = new HashMap<String, String>();
        args.put("user_id", String.valueOf(userId));
        args.put("type_id", String.valueOf(type));
        args.put("type_name", title);
        FlurryAgent.logEvent(FLURRY_EVENT_ADD_BODY_MEASUREMENT, args);
    }

    public void setUserGoals() {
        FlurryAgent.logEvent(FLURRY_EVENT_SET_USER_GOALS);
    }

    public void updateProfile(User user, AppSettings.SystemOfMeasurement units, AppSettings.DateFormat dateFormat) {
        Map<String, String> args = new HashMap<>();

        if (user.hasAge()) {
            args.put("age", String.valueOf(user.getAge()));
        }

        if (user.hasWeight()) {
            args.put("weight", String.format("%.1f", user.getWeight()));
        }

        if (user.hasHeight()) {
            args.put("height", String.valueOf(user.getHeight()));
        }

        if (user.hasWaist()) {
            args.put("waist", String.format("%.1f", user.getWeight()));
        }

        args.put("login", String.valueOf(user.getAccountType()));
        args.put("gender", user.isMale() ? "male" : "female");

        args.put("units", String.valueOf(units));
        args.put("date", String.valueOf(dateFormat));

        Log.d(TAG, FLURRY_EVENT_UPDATE_PROFILE + " " + args);

        FlurryAgent.logEvent(FLURRY_EVENT_UPDATE_PROFILE);
    }

    public void deleteScheduleEntry() {
        FlurryAgent.logEvent(FLURRY_EVENT_DELETE_SCHEDULE_ENTRY);
    }

    public void addScheduleEntry() {
        FlurryAgent.logEvent(FLURRY_EVENT_ADD_SCHEDULE_ENTRY);
    }

    public void shareScheduleEntry() {
        FlurryAgent.logEvent(FLURRY_EVENT_SHARE_SCHEDULE);
    }

    @NonNull
    private Map<String, String> createExerciseArgs(Exercise exercise) {
        Map<String, String> args = new HashMap<String, String>();
        args.put("youtube_id", String.valueOf(exercise.getYoutubeId()));
        args.put("exercise_name", exercise.getName());
        return args;
    }

    @NonNull
    private Map<String, String> createProgramArgs(Program program) {
        Map<String, String> args = new HashMap<>();
        args.put("program_id", String.valueOf(program.getId()));
        args.put("program_name", program.getName());
        return args;
    }
}
