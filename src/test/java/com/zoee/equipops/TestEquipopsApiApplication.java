package com.zoee.equipops;

import org.springframework.boot.SpringApplication;

public class TestEquipopsApiApplication {

    public static void main(String[] args) {
        SpringApplication.from(EquipopsApiApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
