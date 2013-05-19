package com.babeefone;

public class BaseThread extends Thread {
    protected final BabeefoneService babeefoneService;
    protected Boolean canceled = false;

    public BaseThread(BabeefoneService babeefoneService) {
        this.babeefoneService = babeefoneService;
    }

    public void cancel() {
        canceled = true;
    }
}
