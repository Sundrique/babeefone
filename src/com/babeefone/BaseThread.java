package com.babeefone;

public class BaseThread extends Thread {
    protected final MainService mainService;
    protected Boolean canceled = false;

    public BaseThread(MainService mainService) {
        this.mainService = mainService;
    }

    public void cancel() {
        canceled = true;
    }
}
