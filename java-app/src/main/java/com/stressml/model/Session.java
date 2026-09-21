package com.stressml.model;

import java.time.LocalDateTime;

public class Session {
    public int id, userId, breakCount;
    public LocalDateTime startedAt, endedAt;
    public boolean calibration, demo;
    public Integer selfReportStress;
}
