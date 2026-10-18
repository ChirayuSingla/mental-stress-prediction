package com.stressml.config;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AppConfigTest {
    @Test void invalidIntervalsAndModesFailBeforeStartingMonitoring() {
        for(var entry:java.util.Map.of("monitor.window.seconds","0","ml.timeout.ms","-1","app.mode","typo","app.participant.code","=formula").entrySet()){
            String old=System.getProperty(entry.getKey());
            try{System.setProperty(entry.getKey(),entry.getValue());assertThrows(IllegalArgumentException.class,AppConfig::load);}
            finally{if(old==null)System.clearProperty(entry.getKey());else System.setProperty(entry.getKey(),old);}
        }
    }
}
