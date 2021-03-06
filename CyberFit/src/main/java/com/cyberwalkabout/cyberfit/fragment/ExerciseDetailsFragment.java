package com.cyberwalkabout.cyberfit.fragment;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.avast.android.dialogs.fragment.SimpleDialogFragment;
import com.avast.android.dialogs.iface.ISimpleDialogListener;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.cyberwalkabout.cyberfit.AppSettings;
import com.cyberwalkabout.cyberfit.ExerciseVideoScreen;
import com.cyberwalkabout.cyberfit.R;
import com.cyberwalkabout.cyberfit.content.ContentProviderAdapter;
import com.cyberwalkabout.cyberfit.db.sqlite.schema.table.AuthorTable;
import com.cyberwalkabout.cyberfit.db.sqlite.schema.table.ProgramTable;
import com.cyberwalkabout.cyberfit.flurry.FlurryAdapter;
import com.cyberwalkabout.cyberfit.model.v2.Exercise;
import com.cyberwalkabout.cyberfit.model.v2.ExerciseSession;
import com.cyberwalkabout.cyberfit.model.v2.ExerciseState;
import com.cyberwalkabout.cyberfit.model.v2.factory.ExerciseSessionCursorFactory;
import com.cyberwalkabout.cyberfit.util.Const;
import com.cyberwalkabout.cyberfit.util.ConvertUtils;
import com.cyberwalkabout.cyberfit.widget.ExerciseHistoryView;
import com.cyberwalkabout.cyberfit.widget.dialog.DistanceInputDialog;
import com.cyberwalkabout.cyberfit.widget.dialog.RepetitionsInputDialog;
import com.cyberwalkabout.cyberfit.widget.dialog.TimePickerDialog;
import com.cyberwalkabout.cyberfit.widget.dialog.UserWeightInputDialog;
import com.cyberwalkabout.cyberfit.widget.dialog.WeightInputDialog;
import com.cyberwalkabout.cyberfit.youtube.YoutubeThumbnail;
import com.google.common.base.Joiner;

import org.parceler.Parcels;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public class ExerciseDetailsFragment extends Fragment implements ISimpleDialogListener, LoaderManager.LoaderCallbacks<Cursor> {
    private static final String TAG = ExerciseDetailsFragment.class.getSimpleName();

    private static final int REQUEST_POPUP_CONFIRM_RECORD = 1;
    private static final int RECOVERY_DIALOG_REQUEST = 2;

    private static final String TIMER_BUTTON_FORMAT = "%02d:%02d:%02d";
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
    public static final int DESCRIPTION_MAX_CHARACTERS_NOT_COLLAPSIBLE = 240;
    public static final int DESCRIPTION_MAX_LINES_COLLAPSED = 4;

    static {
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT+0"));
    }

    // [hours, minutes, seconds]
    private int[] timerValues = new int[]{0, 0, 0};

    private long timeStart = 0L;
    private long timeElapsed = 0L;
    private long timeLeft = 0;

    private Handler handler = new Handler();
    private TextView timeElapsedText;
    private TextView timeLeftText;

    private View timerContainer;
    private View enterDataContainer;
    private View enterTimeContainer;

    private ExerciseSession exerciseSession;
    private ExerciseSession lastCompletedExerciseSession;

    private ExerciseState exerciseState = ExerciseState.READY_TO_START;

    private Ringtone alarm;
    private Vibrator vibrator;

    private ContentProviderAdapter contentProviderAdapter = ContentProviderAdapter.getInstance();

    private AppSettings appSettings;
    private Button enterWeightBtn;
    private Button enterDistanceBtn;
    private Button enterRepsBtn;
    private Exercise exercise;
    private Button anotherSetBtn;
    private ImageView expandCollapseDescriptionBtn;

    private Button triggerExercise;
    private TextView dataToEnterNotice;
    private Button enterTimeButton;
    private TextView exerciseNameText;
    private TextView descriptionText;
    private ImageView exerciseImage;
    private ExerciseHistoryView exerciseHistoryView;
    private TextView programNamesText;
    private TextView authorTextView;
    private View exerciseInfoContainer;

    private MenuItem favoriteMenuItem;

    private boolean isFavorite;

    private FlurryAdapter flurryAdapter = FlurryAdapter.getInstance();

    private Runnable updateTimerTask = new Runnable() {
        public void run() {
            // TODO: move magic numbers to constants

            timeElapsed = System.currentTimeMillis() - timeStart;
            exerciseSession.setTime(timeElapsed);
            timeLeft -= 1000;

            if (timeLeft < 0) {
                timeLeft = 0;
            }

            // TODO: what will happen after 24h elapsed
            timeElapsedText.setText(dateFormat.format(new Date(timeElapsed)));
            timeLeftText.setText(dateFormat.format(new Date(timeLeft)));
            handler.postDelayed(this, 1000);

            if (isTimerSet() && timeLeft == 3000) {
                vibrator.vibrate(3000);
                alarm.play();
            }

            if (isTimerSet() && timeLeft == 0) {
                moveToTimeRecordedState();
                exerciseSession.setState(exerciseState);

                contentProviderAdapter.updateExerciseSession(getActivity(), exerciseSession, false);
            }
        }
    };

    public ExerciseDetailsFragment() {
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appSettings = new AppSettings(getActivity());

        Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        alarm = RingtoneManager.getRingtone(getActivity().getApplicationContext(), notification);
        vibrator = (Vibrator) getActivity().getSystemService(Context.VIBRATOR_SERVICE);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (exerciseState != ExerciseState.DONE && exerciseState != ExerciseState.READY_TO_START) {
            contentProviderAdapter.updateExerciseSession(getActivity(), exerciseSession, false);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.exercise_details, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        anotherSetBtn = (Button) view.findViewById(R.id.another_set_btn);

        enterDataContainer = view.findViewById(R.id.enter_data_container);
        enterDistanceBtn = (Button) enterDataContainer.findViewById(R.id.enter_distance_btn);
        enterRepsBtn = (Button) enterDataContainer.findViewById(R.id.enter_reps_btn);
        enterWeightBtn = (Button) enterDataContainer.findViewById(R.id.enter_weight_btn);

        enterTimeContainer = view.findViewById(R.id.enter_time_container);
        timerContainer = view.findViewById(R.id.timer_container);
        timeElapsedText = (TextView) view.findViewById(R.id.time_elapsed);
        timeLeftText = (TextView) view.findViewById(R.id.time_left);

        exerciseImage = (ImageView) view.findViewById(R.id.exercise_image);
        descriptionText = (TextView) view.findViewById(R.id.exercise_description);
        exerciseNameText = (TextView) view.findViewById(R.id.exercise_name);

        enterTimeButton = (Button) view.findViewById(R.id.enter_time_btn);
        dataToEnterNotice = (TextView) view.findViewById(R.id.exercise_details_to_enter_notice);
        triggerExercise = (Button) view.findViewById(R.id.trigger_exercise_btn);

        exerciseHistoryView = (ExerciseHistoryView) view.findViewById(R.id.todays_history);

        expandCollapseDescriptionBtn = (ImageView) view.findViewById(R.id.expand_collapse_description);

        programNamesText = (TextView) view.findViewById(R.id.program_names);
        authorTextView = (TextView) view.findViewById(R.id.author);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        Intent intent = getActivity().getIntent();

        if (intent == null) {
            getActivity().finish();
        } else {
            exercise = Parcels.unwrap(intent.getParcelableExtra(Const.EXERCISE));
            isFavorite = intent.getBooleanExtra(Const.IS_FAVORITE, false);

            setupActionBar();

            exerciseInfoContainer = getView().findViewById(R.id.exercise_info_container);

            initName();
            initDescription();
            displayExerciseImage();
            initTimeButton();
            initTriggerExerciseButton();

            exerciseHistoryView.setNoticeButtonCLickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                /*SimpleDialogFragment.createBuilder(getActivity(), getActivity().getSupportFragmentManager()).setMessage(R.string.exercise_notice)
                        .setPositiveButtonText(android.R.string.ok).show();*/
                }
            });

            anotherSetBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    doneExercise();

                    exerciseState = ExerciseState.READY_TO_START;
                    Bundle args = new Bundle();
                    args.putString(Const.EXERCISE_ID, exercise.getId());
                    getLoaderManager().restartLoader(ContentProviderAdapter.LOADER_EXERCISE_IN_PROGRESS, args, ExerciseDetailsFragment.this);
                    getLoaderManager().restartLoader(ContentProviderAdapter.LOADER_MOST_RECENT_COMPLETED_EXERCISE, args, ExerciseDetailsFragment.this);
                }
            });

            Bundle args = new Bundle();
            args.putString(Const.EXERCISE_ID, exercise.getId());

            getLoaderManager().initLoader(ContentProviderAdapter.LOADER_MOST_RECENT_COMPLETED_EXERCISE, args, this);
            getLoaderManager().initLoader(ContentProviderAdapter.LOADER_EXERCISE_IN_PROGRESS, args, this);
            getLoaderManager().initLoader(ContentProviderAdapter.LOADER_PROGRAMS_BY_EXERCISE, args, this);
            getLoaderManager().initLoader(ContentProviderAdapter.LOADER_AUTHOR_BY_EXERCISE, args, this);

            updateUIState();
        }
    }

    private void initTimeButton() {
        enterTimeButton.setText(String.format(TIMER_BUTTON_FORMAT, timerValues[0], timerValues[1], timerValues[2]));
        enterTimeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                TimePickerDialog timePickerDialog = TimePickerDialog.create(getActivity(), timerValues[0], timerValues[1], timerValues[2]);
                timePickerDialog.setListener(new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(View view, int hours, int minutes, int seconds) {
                        timerValues[0] = hours;
                        timerValues[1] = minutes;
                        timerValues[2] = seconds;

                        Button button = (Button) v;
                        button.setText(String.format(TIMER_BUTTON_FORMAT, hours, minutes, seconds));
                    }
                });
                timePickerDialog.show(getActivity().getSupportFragmentManager(), TimePickerDialog.TAG);
            }
        });
    }

    private void initName() {
        String name = exercise.getName().trim();
        exerciseNameText.setText(name);
    }

    private void initDescription() {
        String description = exercise.getDescription().trim();
        descriptionText.setText(description);

        if (description.length() > DESCRIPTION_MAX_CHARACTERS_NOT_COLLAPSIBLE) {
            expandCollapseDescriptionBtn.setVisibility(View.VISIBLE);
            descriptionText.setMaxLines(DESCRIPTION_MAX_LINES_COLLAPSED);

            expandCollapseDescriptionBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (descriptionText.getTag() == null || (Boolean) descriptionText.getTag() == false) {
                        descriptionText.setMaxLines(Integer.MAX_VALUE);
                        expandCollapseDescriptionBtn.setImageResource(R.drawable.arrow_up);
                        descriptionText.setTag(true);
                    } else {
                        descriptionText.setMaxLines(4);
                        expandCollapseDescriptionBtn.setImageResource(R.drawable.arrow_down);
                        descriptionText.setTag(false);
                    }
                }
            });
        } else {
            expandCollapseDescriptionBtn.setVisibility(View.GONE);
            descriptionText.setMaxLines(Integer.MAX_VALUE);
        }
    }

    private void displayExerciseImage() {
        String thumbnailUrl = YoutubeThumbnail.MQDEFAULT.toURL(exercise.getYoutubeId());
        Glide.with(ExerciseDetailsFragment.this)
                .load(thumbnailUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop()
                .dontAnimate()
                .skipMemoryCache(true)
                .into(exerciseImage);

        exerciseImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getActivity(), ExerciseVideoScreen.class).putExtra(Const.EXERCISE, Parcels.wrap(exercise)));
            }
        });
    }

    private void setupActionBar() {
        ActionBar supportActionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setTitle(getString(R.string.exercise_details_screen_title));
            supportActionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    private void resetToInitialState() {
        this.exerciseSession = new ExerciseSession();
        this.exerciseSession.setExerciseId(exercise.getId());
        this.exerciseSession.setState(exerciseState);
        // this.exerciseSession.setYoutubeId(exercise.getYoutubeId());

        exerciseState = ExerciseState.READY_TO_START;

        /*Bundle args = new Bundle();
        args.putLong(ContentProviderAdapter.Const.EXERCISE_ID, exercise.getId());
        getLoaderManager().restartLoader(ContentProviderAdapter.LOADER_EXERCISE_IN_PROGRESS, args, this);*/

        initEnterDataContainer();
        updateUIState();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.exercise_details_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        favoriteMenuItem = menu.findItem(R.id.action_favorite);
        initActionBarFavoriteIcon();
    }

    private void initActionBarFavoriteIcon() {
        favoriteMenuItem.setIcon(isFavorite ? R.drawable.ic_favorite_selected : R.drawable.ic_favorite_unselected);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_favorite) {
            if (isFavorite) {
                contentProviderAdapter.unfavoriteExercise(getActivity(), exercise.getId());
                item.setIcon(R.drawable.ic_favorite_unselected);
            } else {
                contentProviderAdapter.favoriteExercise(getActivity(), exercise.getId());
                item.setIcon(R.drawable.ic_favorite_selected);
            }

            isFavorite = !isFavorite;
            getActivity().supportInvalidateOptionsMenu();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean isTimerSet() {
        return timerValues[0] != 0 || timerValues[1] != 0 || timerValues[2] != 0;
    }

    private long getTimerValue() {
        return (timerValues[0] * 60 * 60 * 1000) + (timerValues[1] * 60 * 1000) + (timerValues[2] * 1000);
    }

    private void loadHistory() {
        Bundle args = new Bundle();
        args.putString(Const.EXERCISE_ID, exercise.getId());
        getLoaderManager().restartLoader(ContentProviderAdapter.LOADER_MOST_RECENT_COMPLETED_EXERCISE, args, this);
    }

    private void initTriggerExerciseButton() {
        triggerExercise.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (exerciseState) {
                    case READY_TO_START:
                        moveToStartedState();
                        break;
                    case STARTED:
                        moveToTimeRecordedState();
                        break;
                    case TIME_RECORDED:
                        doneExercise();
                        getActivity().finish();
                        break;
                    /*case DONE:
                        getActivity().finish();
                        break;*/
                }

                triggerExercise.setBackgroundDrawable(getResources().getDrawable(R.drawable.button_red));

                exerciseSession.setState(exerciseState);

                contentProviderAdapter.updateExerciseSession(getActivity(), exerciseSession, false);

                updateUIState();
            }
        });
    }

    /*private void moveToDoneState() {
        *//*if (exercise.isTrackTime() && !exercise.isTrackRepetitions() && exerciseSession.getTime() < 5000) {
            showConfirmationPopup();
        } else {*//*
        doneExercise();
        //}
    }*/

    private void moveToStartedState() {
        exerciseState = ExerciseState.STARTED;
        timeStart = System.currentTimeMillis();
        handler.postDelayed(updateTimerTask, 0);
        exerciseSession.setTimestampStarted(timeStart);
    }

    private void moveToTimeRecordedState() {
        vibrator.cancel();

        if (alarm.isPlaying()) {
            alarm.stop();
        }

        exerciseState = ExerciseState.TIME_RECORDED;

        if (exercise.isTrackRepetitions() || exercise.isTrackWeight() || exercise.isTrackDistance()) {
            triggerExercise.setVisibility(View.GONE);
        }

        triggerExercise.setText(R.string.done);

        timeLeft = timeStart = 0;
        handler.removeCallbacks(updateTimerTask);

        enterTimeContainer.setVisibility(View.GONE);
        timerContainer.setVisibility(View.GONE);
        dataToEnterNotice.setVisibility(View.GONE);

        enterDataContainer.setVisibility(View.VISIBLE);

        setupEnterDataFields(exercise);
    }

    private void doneExercise() {
        exerciseState = ExerciseState.DONE;

        exerciseSession.setTimestampCompleted(System.currentTimeMillis());
        exerciseSession.setTime(isTimerSet() ? getTimerValue() : timeElapsed);
        exerciseSession.setState(exerciseState);

        contentProviderAdapter.updateExerciseSession(getActivity(), exerciseSession, false);

        flurryAdapter.exerciseCompleted(exercise, exerciseSession);
    }

    private void updateUIState() {
        switch (exerciseState) {
            case READY_TO_START: {
                exerciseInfoContainer.setVisibility(View.VISIBLE);
                triggerExercise.setBackgroundResource(R.drawable.button_green);

                timerContainer.setVisibility(View.GONE);
                enterTimeContainer.setVisibility(View.VISIBLE);
                enterDataContainer.setVisibility(View.GONE);
                dataToEnterNotice.setVisibility(View.VISIBLE);
                anotherSetBtn.setVisibility(View.GONE);
                triggerExercise.setText(R.string.start);

                initEnterDataContainer();
            }
            break;

            case STARTED: {
                exerciseInfoContainer.setVisibility(View.VISIBLE);
                triggerExercise.setBackgroundResource(R.drawable.button_red);
                triggerExercise.setText(R.string.stop);
                timerContainer.setVisibility(View.VISIBLE);
                enterTimeContainer.setVisibility(View.GONE);
                if (timerValues[0] == 0 && timerValues[1] == 0 && timerValues[2] == 0) {
                    timerContainer.findViewById(R.id.time_left_container).setVisibility(View.GONE);
                    timerContainer.findViewById(R.id.time_elapsed_container).setVisibility(View.VISIBLE);
                } else {
                    timeLeft = getTimerValue();
                    timerContainer.findViewById(R.id.time_left_container).setVisibility(View.VISIBLE);
                    timerContainer.findViewById(R.id.time_elapsed_container).setVisibility(View.GONE);
                }
                if (validateExerciseData()) {
                    anotherSetBtn.setVisibility(View.VISIBLE);
                    triggerExercise.setText(R.string.finished);
                    triggerExercise.setVisibility(View.VISIBLE);
                }
            }
            break;

            case TIME_RECORDED: {
                exerciseInfoContainer.setVisibility(View.GONE);

                triggerExercise.setBackgroundResource(R.drawable.button_red);
                enterTimeContainer.setVisibility(View.GONE);
                timerContainer.setVisibility(View.GONE);
                enterDataContainer.setVisibility(View.VISIBLE);
                dataToEnterNotice.setVisibility(View.GONE);

                setupEnterDataFields(exercise);

                // init exercise record with last known data
                if (!exerciseSession.hasRepetitions()) {
                    exerciseSession.setRepetitions(getLastKnownRepetitions());
                }

                if (!exerciseSession.hasWeight()) {
                    exerciseSession.setWeight(getLastKnownWeight());
                }

                if (!exerciseSession.hasDistance()) {
                    exerciseSession.setDistance(getLastKnownDistance());
                }

                timeLeft = timeStart = 0;
                handler.removeCallbacks(updateTimerTask);

                validateAndDisplayButtons();
            }
            break;
            /*case DONE: {
                enterDataContainer.setVisibility(View.GONE);
                validateAndDisplayButtons();
            }*/
        }
    }

    private void validateAndDisplayButtons() {
        if (validateExerciseData()) {
            anotherSetBtn.setVisibility(View.VISIBLE);

            triggerExercise.setText(R.string.finished);
            triggerExercise.setVisibility(View.VISIBLE);
            triggerExercise.setBackgroundResource(R.drawable.button_red);
        } else {
            triggerExercise.setVisibility(View.GONE);
            anotherSetBtn.setVisibility(View.GONE);
        }
    }

    private void setupEnterDataFields(Exercise exercise) {
        enterDataContainer.findViewById(R.id.weight_container).setVisibility(exercise.isTrackWeight() ? View.VISIBLE : View.GONE);
        enterDataContainer.findViewById(R.id.distance_container).setVisibility(exercise.isTrackDistance() ? View.VISIBLE : View.GONE);
        enterDataContainer.findViewById(R.id.reps_container).setVisibility(exercise.isTrackRepetitions() ? View.VISIBLE : View.GONE);

        AppSettings.SystemOfMeasurement som = appSettings.getSystemOfMeasurement();


        Integer reps = null;
        Double weight = null;
        Double distance = null;

        if (exerciseSession != null) {
            if (exerciseSession.hasRepetitions()) {
                reps = exerciseSession.getRepetitions();
            }
            if (exerciseSession.hasWeight()) {
                weight = exerciseSession.getWeight();
            }
            if (exerciseSession.hasDistance()) {
                distance = exerciseSession.getDistance();
            }
        }

        if (reps == null) {
            reps = getLastKnownRepetitions();
        }

        if (weight == null) {
            weight = getLastKnownWeight();
        }

        if (distance == null) {
            distance = getLastKnownDistance();
        }

        enterRepsBtn.setText(String.valueOf(reps));
        enterWeightBtn.setText(String.format("%.1f %s", weight, getString(som.getWeightUnitResource())));
        enterDistanceBtn.setText(String.format("%.1f %s", distance, getString(som.getDistanceUnitResource())));
    }

    private void initEnterDataContainer() {
        final AppSettings.SystemOfMeasurement som = appSettings.getSystemOfMeasurement();

        dataToEnterNotice.setText(getString(R.string.exercise_details_to_enter_notice, createNotice(exercise)));

        if (exerciseSession != null) {
            enterRepsBtn.setText(String.valueOf(exerciseSession.getRepetitions()));
            enterWeightBtn.setText(String.format("%.1f %s", exerciseSession.getWeight(), getString(som.getWeightUnitResource())));
            enterDistanceBtn.setText(String.format("%.1f %s", exerciseSession.getDistance(), getString(som.getDistanceUnitResource())));
        }

        View.OnClickListener onClickListener = new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                final AppSettings.SystemOfMeasurement som = appSettings.getSystemOfMeasurement();

                if (v == enterRepsBtn) {
                    int lastReps = getLastKnownRepetitions();
                    RepetitionsInputDialog repetitionsInputDialog = RepetitionsInputDialog.create(getActivity(), lastReps);
                    repetitionsInputDialog.setListener(new RepetitionsInputDialog.OnRepetitionsSetListener() {
                        @Override
                        public void onRepetitionsSet(View v, int repetitions) {
                            exerciseSession.setRepetitions(repetitions);
                            enterRepsBtn.setText(String.valueOf(repetitions));
                            if (validateExerciseData()) {
                                //moveToDoneState();
                                updateUIState();
                            }
                        }
                    });
                    repetitionsInputDialog.show(getActivity().getSupportFragmentManager(), RepetitionsInputDialog.TAG);

                } else if (v == enterWeightBtn) {
                    double lastUsedWeight = 0;
                    if (exerciseSession.getWeight() != null) {
                        lastUsedWeight = exerciseSession.getWeight();
                    } else {
                        lastUsedWeight = getLastKnownWeight();
                    }

                    WeightInputDialog weightInputDialog = WeightInputDialog.create(getActivity(), (int) lastUsedWeight);
                    weightInputDialog.setListener(new WeightInputDialog.OnWeightSetListener() {
                        @Override
                        public void onWeightSet(View v, float weight, String unit) {
                            if (som == AppSettings.SystemOfMeasurement.METRIC) {
                                exerciseSession.setWeight((double) weight);
                            } else {
                                exerciseSession.setWeight(ConvertUtils.lbsToKg(weight));
                            }
                            enterWeightBtn.setText(String.format("%.1f %s", weight, getString(som.getWeightUnitResource())));
                            if (validateExerciseData()) {
                                //moveToDoneState();
                                updateUIState();
                            }
                        }
                    });
                    weightInputDialog.show(getActivity().getSupportFragmentManager(), UserWeightInputDialog.TAG);
                } else if (v == enterDistanceBtn) {
                    DistanceInputDialog distanceInputDialog = DistanceInputDialog.create(getActivity());
                    distanceInputDialog.setListener(new DistanceInputDialog.OnDistanceSetListener() {
                        @Override
                        public void onDistanceSet(View v, float distance, String unit) {
                            if (som == AppSettings.SystemOfMeasurement.METRIC) {
                                exerciseSession.setDistance((double) distance);
                            } else {
                                exerciseSession.setDistance(ConvertUtils.milesToKm(distance));
                            }
                            enterDistanceBtn.setText(String.format("%.1f %s", distance, getString(som.getDistanceUnitResource())));
                            if (validateExerciseData()) {
                                //moveToDoneState();
                                updateUIState();
                            }
                        }
                    });
                    distanceInputDialog.show(getActivity().getSupportFragmentManager(), DistanceInputDialog.TAG);
                }
            }
        };

        enterRepsBtn.setOnClickListener(onClickListener);
        enterWeightBtn.setOnClickListener(onClickListener);
        enterDistanceBtn.setOnClickListener(onClickListener);
    }

    private Double getLastKnownDistance() {
        return lastCompletedExerciseSession != null && lastCompletedExerciseSession.getDistance() != null ? lastCompletedExerciseSession.getDistance() : 0;
    }

    private double getLastKnownWeight() {
        final AppSettings.SystemOfMeasurement som = appSettings.getSystemOfMeasurement();
        if (lastCompletedExerciseSession != null && lastCompletedExerciseSession.getWeight() != null) {
            return som == AppSettings.SystemOfMeasurement.US ? lastCompletedExerciseSession.getWeight() : ConvertUtils.lbsToKg(lastCompletedExerciseSession.getWeight());
        }
        return 0;
    }

    private int getLastKnownRepetitions() {
        return lastCompletedExerciseSession != null && lastCompletedExerciseSession.getRepetitions() != null ? lastCompletedExerciseSession.getRepetitions() : 0;
    }

    private boolean validateExerciseData() {
        boolean valid = true;
        if (exercise.isTrackRepetitions() && (exerciseSession.getRepetitions() == null || exerciseSession.getRepetitions() == 0)) {
            valid = false;
        } else if (exercise.isTrackWeight() && exerciseSession.getWeight() == null) {
            valid = false;
        } else if (exercise.isTrackDistance() && (exerciseSession.getDistance() == null || exerciseSession.getDistance() == 0)) {
            valid = false;
        }
        return valid;
    }

    private String createNotice(Exercise exercise) {
        StringBuilder notice = new StringBuilder();
        if (exercise.isTrackWeight()) {
            notice.append(getString(R.string.weight));
        }
        if (exercise.isTrackDistance()) {
            if (notice.length() > 0) {
                notice.append(" ").append(getString(R.string.and)).append(" ");
            }
            notice.append(getString(R.string.distance).toLowerCase());
        }
        if (exercise.isTrackRepetitions()) {
            if (notice.length() > 0) {
                notice.append(" ").append(getString(R.string.and)).append(" ");
            }
            notice.append(getString(R.string.repetitions));
        }
        return notice.toString();
    }

    @Override
    public void onPositiveButtonClicked(int requestCode) {
        if (requestCode == REQUEST_POPUP_CONFIRM_RECORD) {
            doneExercise();
            exerciseSession.setState(exerciseState);
            contentProviderAdapter.updateExerciseSession(getActivity(), exerciseSession, false);
            loadHistory();
        }
    }

    @Override
    public void onNegativeButtonClicked(int requestCode) {
        if (requestCode == REQUEST_POPUP_CONFIRM_RECORD) {
            contentProviderAdapter.deleteExerciseSessionById(getActivity(), exerciseSession.getId());
            enterDataContainer.setVisibility(View.GONE);
            anotherSetBtn.setVisibility(View.VISIBLE);

            triggerExercise.setText(R.string.finished);
        }
    }

    private void showConfirmationPopup() {
        SimpleDialogFragment.createBuilder(getActivity(), getActivity().getSupportFragmentManager()).setMessage(R.string.save_exercise_history_notice)
                .setPositiveButtonText(android.R.string.yes)
                .setNegativeButtonText(android.R.string.no).setTargetFragment(ExerciseDetailsFragment.this, REQUEST_POPUP_CONFIRM_RECORD).show();
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        switch (id) {
            case ContentProviderAdapter.LOADER_MOST_RECENT_COMPLETED_EXERCISE: {
                return ContentProviderAdapter.getInstance().loaderMostRecentCompletedExerciseSession(getActivity(), args);
            }
            case ContentProviderAdapter.LOADER_EXERCISE_IN_PROGRESS: {
                return ContentProviderAdapter.getInstance().loaderInProgressExerciseHistoryRecord(getActivity(), args);
            }
            case ContentProviderAdapter.LOADER_PROGRAMS_BY_EXERCISE: {
                return ContentProviderAdapter.getInstance().loaderProgramsByExerciseId(getActivity(), args);
            }
            case ContentProviderAdapter.LOADER_AUTHOR_BY_EXERCISE: {
                return ContentProviderAdapter.getInstance().loaderAuthorByExerciseId(getActivity(), args);
            }
            default:
                return null;
        }
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        switch (loader.getId()) {
            case ContentProviderAdapter.LOADER_MOST_RECENT_COMPLETED_EXERCISE: {
                if (data.moveToNext()) {
                    lastCompletedExerciseSession = ExerciseSessionCursorFactory.getInstance().create(data);

                    ExerciseHistoryView.ExerciseHistoryAdapter adapter = new ExerciseHistoryView.ExerciseHistoryAdapter(getActivity(), exercise, Collections.singletonList(lastCompletedExerciseSession));
                    exerciseHistoryView.setAdapter(adapter);

                    if (exerciseState == ExerciseState.TIME_RECORDED) {
                        setupEnterDataFields(exercise);
                        updateUIState();
                        //loadHistory();
                    }
                }
            }
            break;
            case ContentProviderAdapter.LOADER_EXERCISE_IN_PROGRESS: {
                if (data.getCount() > 0 && data.moveToFirst()) {
                    exerciseSession = ExerciseSessionCursorFactory.getInstance().create(data);
                    if (exerciseSession.getState() != null) {
                        exerciseState = exerciseSession.getState();
                    }
                } else {
                    // there is no 'in progress' exercises
                    this.exerciseSession = new ExerciseSession();
                    this.exerciseSession.setExerciseId(exercise.getId());
                    this.exerciseSession.setState(exerciseState);
                    // this.exerciseSession.setYoutubeId(exercise.getYoutubeId());
                }

                exerciseHistoryView.setNoticeButtonCLickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        SimpleDialogFragment.createBuilder(getActivity(), getActivity().getSupportFragmentManager()).setMessage(R.string.exercise_notice)
                                .setPositiveButtonText(android.R.string.ok).show();
                    }
                });

                initEnterDataContainer();
                updateUIState();
            }
            break;
            case ContentProviderAdapter.LOADER_PROGRAMS_BY_EXERCISE: {
                if (data.getCount() > 0) {
                    List<String> programNames = new ArrayList<>(data.getCount());
                    while (data.moveToNext()) {
                        programNames.add(data.getString(data.getColumnIndex(ProgramTable.COLUMN_NAME)));
                    }
                    programNamesText.setText(Joiner.on(", ").join(programNames));
                    programNamesText.setVisibility(View.VISIBLE);
                } else {
                    programNamesText.setVisibility(View.GONE);
                }
            }
            break;
            case ContentProviderAdapter.LOADER_AUTHOR_BY_EXERCISE: {
                if (data.getCount() > 0) {
                    if (data.moveToFirst()) {
                        String authorName = data.getString(data.getColumnIndex(AuthorTable.COLUMN_NAME));
                        authorTextView.setText(getString(R.string.by) + " " + authorName);
                    }
                }
            }
            break;
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
    }

    @Override
    public void onNeutralButtonClicked(int i) {

    }
}
