package com.zoee.equipops;

import cn.hutool.crypto.digest.BCrypt;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EquipopsApiApplication {

    public static void main(String[] args) {

//        System.out.println(BCrypt.hashpw("...."));

        SpringApplication.run(EquipopsApiApplication.class, args);
    }

}
