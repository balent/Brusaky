package cz.balent.brusaky;

import android.app.Activity;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.TimePicker;

public class BrusakyActivity extends Activity {
    private int crunchesTime = 75;
    private int restTime = 30;
    private int totalSeries = 4;

    private Button timeButton;
    private Button restButton;
    private Button plusButton;
    private Button minusButton;
    private Button startButton;

    private TextView serieLengthTextView;
    private TextView restLengthTextView;
    private TextView seriesCountTextView;
    private TextView statusLabelTextView;
    private TextView timeCounter;

    private enum SoundType {
        WAIT, START, STOP;
    }

    private enum AppState {
        STOPPED, FORCE_STOP, PREPARE, CRUNCHES, REST;
    }

    private AppState appState = AppState.STOPPED;
    private int counter = 0;
    private int seriesCounter = 0;
    private PowerManager pm;
    private PowerManager.WakeLock wl;

    private TimePickerDialog.OnTimeSetListener crunchesTimeSetListener = new TimePickerDialog.OnTimeSetListener() {
        public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
            crunchesTime = (hourOfDay * 60) + minute;
            setTextCorrectly(serieLengthTextView, getStringTime(crunchesTime));
        }
    };

    private TimePickerDialog.OnTimeSetListener restTimeSetListener = new TimePickerDialog.OnTimeSetListener() {
        public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
            restTime = (hourOfDay * 60) + minute;
            setTextCorrectly(restLengthTextView, getStringTime(restTime));
        }
    };

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "Robo Tag");

        timeButton = (Button) findViewById(R.id.button1);
        restButton = (Button) findViewById(R.id.button2);
        plusButton = (Button) findViewById(R.id.button5);
        minusButton = (Button) findViewById(R.id.button10);
        startButton = (Button) findViewById(R.id.button4);

        serieLengthTextView = (TextView) findViewById(R.id.textView1a);
        restLengthTextView = (TextView) findViewById(R.id.textView2a);
        seriesCountTextView = (TextView) findViewById(R.id.textView3a);
        statusLabelTextView = (TextView) findViewById(R.id.textView4);
        timeCounter = (TextView) findViewById(R.id.textView5);

        class ExerciseRunner extends Thread {

            private void sleepMiliSeconds(int i) {
                try {
                    Thread.sleep(i);
                } catch (InterruptedException ex) {
                    // nothing
                }
            }

            private void playSound(final SoundType st) {
                Runnable r = new Runnable() {
                    public void run() {
                        MediaPlayer mediaPlayer = null;

                        if (st == SoundType.WAIT) {
                            mediaPlayer = MediaPlayer.create(getApplicationContext(), R.raw.prepare);
                        } else if (st == SoundType.START) {
                            mediaPlayer = MediaPlayer.create(getApplicationContext(), R.raw.start);
                        } else if (st == SoundType.STOP) {
                            mediaPlayer = MediaPlayer.create(getApplicationContext(), R.raw.end);
                        }
                        mediaPlayer.start();
                        while (mediaPlayer.isPlaying()) {
                            sleepMiliSeconds(1000);
                        }
                        mediaPlayer.stop();
                        mediaPlayer.release();
                        mediaPlayer = null;
                    }
                };
                Thread t = new Thread(r);
                t.start();
            }

            private void setStopped() {
                setTextCorrectly(statusLabelTextView, getString(R.string.time_stopped));
                setTextCorrectly(startButton, getString(R.string.start));
                updateTime(0);
                wl.release();
            }

            private void setPrepare() {
                setTextCorrectly(statusLabelTextView, getString(R.string.prepare));
                updateTime(counter);
                playSound(SoundType.WAIT);
            }

            private void setCrunches() {
                setTextCorrectly(statusLabelTextView, getString(R.string.work));
                updateTime(counter);
                if (counter == crunchesTime) {
                    playSound(SoundType.START);
                }
            }

            private void setRest() {
                updateTime(counter + 4);
                setTextCorrectly(statusLabelTextView, getString(R.string.rest));
                if (counter + 4 == restTime) {
                    playSound(SoundType.STOP);
                }
            }

            private void setState(AppState appState) {
                if (appState == AppState.STOPPED) {
                    setStopped();
                } else if (appState == AppState.PREPARE) {
                    setPrepare();
                } else if (appState == AppState.CRUNCHES) {
                    setCrunches();
                } else if (appState == AppState.REST) {
                    setRest();
                }
            }

            public void run() {
                sleepMiliSeconds(1000);

                while (true) {
                    // Simple state machine
                    if (appState == AppState.FORCE_STOP) {
                        appState = AppState.STOPPED;
                        seriesCounter = 0;
                        counter = 0;
                        setStopped();
                        return;
                    }

                    if (counter == 0) {
                        if ((appState == AppState.CRUNCHES)
                                && (seriesCounter == totalSeries)) {
                            appState = AppState.STOPPED;
                            seriesCounter = 0;
                            counter = 0;
                            // Play sound at the end of all series 
                            playSound(SoundType.STOP);
                            setStopped();
                            return;
                        }

                        if (appState == AppState.STOPPED) {
                            appState = AppState.PREPARE;
                            counter = 4;
                        } else if (appState == AppState.PREPARE) {
                            appState = AppState.CRUNCHES;
                            counter = crunchesTime;
                            seriesCounter++;
                        } else if (appState == AppState.CRUNCHES) {
                            appState = AppState.REST;
                            counter = restTime - 4;
                        } else if (appState == AppState.REST) {
                            appState = AppState.PREPARE;
                            counter = 4;
                        }
                    }
                    setState(appState);
                    counter--;
                    sleepMiliSeconds(1000);
                }
            }
        }
        ;

        startButton.setOnClickListener(new View.OnClickListener() {
            private ExerciseRunner exerciseRunner;

            public void onClick(View v) {

                if (appState == AppState.STOPPED) {
                    wl.acquire();
                    startButton.setText(getString(R.string.stop));
                    exerciseRunner = new ExerciseRunner();
                    exerciseRunner.start();
                } else {
                    startButton.setText(getString(R.string.start));
                    appState = AppState.FORCE_STOP;
                }
            }
        });

        timeButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showDialog(1);
            }
        });

        restButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showDialog(2);
            }
        });

        plusButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                totalSeries++;
                setTextCorrectly(seriesCountTextView, String.valueOf(totalSeries));
            }
        });

        minusButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (totalSeries > 1) {
                    totalSeries--;
                    setTextCorrectly(seriesCountTextView, String.valueOf(totalSeries));
                }
            }
        });

    }

    @Override
    protected Dialog onCreateDialog(int id) {
        TimePickerDialog tpd;
        if (id == 1) {
            tpd = new TimePickerDialog(this, crunchesTimeSetListener, crunchesTime / 60, crunchesTime % 60, true);
        } else {
            tpd = new TimePickerDialog(this, restTimeSetListener, restTime / 60, restTime % 60, true);
        }
        return tpd;
    }

    private String getStringTime(int seconds) {
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    private void updateTime(int seconds) {
        setTextCorrectly(timeCounter, getStringTime(seconds));
    }

    private void setTextCorrectly(final TextView textView, final String text) {
        textView.post(new Runnable() {
            public void run() {
                textView.setText(text);
            }
        });
    }
}