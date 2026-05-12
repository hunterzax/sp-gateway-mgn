package com.ptt.gateway.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dev")
@Profile("dev")
public class DevMonitorController {

    @GetMapping("/monitor")
    public Resource getMonitorPage() {
        return new ClassPathResource("dev/monitor.html");
    }
}
