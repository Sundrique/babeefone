package com.babeefone;

import android.os.Binder;

public class ServiceBinder extends Binder {
    private MainService service;

    public ServiceBinder(MainService service) {
        super();

        this.service = service;
    }

    public MainService getService() {
        return service;
    }
}
