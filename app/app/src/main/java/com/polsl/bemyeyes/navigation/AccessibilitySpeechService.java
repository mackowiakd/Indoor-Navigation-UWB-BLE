package com.polsl.bemyeyes.navigation;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import java.util.Locale;

/**
 * Narzędzie integrujące natywny syntezator mowy Google TTS z mechanizmami kolejkowania powiadomień.
 */
public class AccessibilitySpeechService implements TextToSpeech.OnInitListener {

    private TextToSpeech textToSpeechEngine;
    private boolean engineReady = false;

    public AccessibilitySpeechService(Context applicationContext) {
        textToSpeechEngine = new TextToSpeech(applicationContext, this);
    }

    @Override
    public void onInit(int initStatus) {
        if (initStatus == TextToSpeech.SUCCESS) {
            int localeStatus = textToSpeechEngine.setLanguage(new Locale("pl", "PL"));
            if (localeStatus == TextToSpeech.LANG_MISSING_DATA || localeStatus == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS_SERVICE", "Brak obsługi języka polskiego lub brak pakietów danych.");
            } else {
                engineReady = true;
                announceImportant("Asystent wewnątrzbudynkowy aktywny. Możesz wybrać cel podróży.");
            }
        }
    }

    /**
     * Używane dla natychmiastowych komunikatów przerywających aktualną wypowiedź.
     */
    public void announceImportant(String messageContent) {
        if (engineReady) {
            textToSpeechEngine.speak(messageContent, TextToSpeech.QUEUE_FLUSH, null, "IMPORTANT_MSG");
        }
    }

    /**
     * Używane dla komunikatów nawigacyjnych tła (np. dodanie mijanej sali do kolejki).
     */
    public void announceBackground(String messageContent) {
        if (engineReady) {
            textToSpeechEngine.speak(messageContent, TextToSpeech.QUEUE_ADD, null, "BACKGROUND_MSG");
        }
    }

    public void terminateService() {
        if (textToSpeechEngine != null) {
            textToSpeechEngine.stop();
            textToSpeechEngine.shutdown();
        }
    }
}
