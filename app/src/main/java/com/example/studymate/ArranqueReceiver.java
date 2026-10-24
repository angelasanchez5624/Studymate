package com.example.studymate;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Android borra las alarmas al apagar el teléfono: al encenderlo se vuelven a programar. */
public class ArranqueReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context c, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Recordatorios.reprogramarGuardadas(c);
            Recordatorios.reprogramarClasesGuardadas(c);
        }
    }
}
