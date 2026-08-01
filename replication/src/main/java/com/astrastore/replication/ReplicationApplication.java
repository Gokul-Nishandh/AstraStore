package com.astrastore.replication;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ReplicationApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReplicationApplication.class, args);
    }

}
