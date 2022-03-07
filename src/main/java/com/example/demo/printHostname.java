package com.example.demo;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class printHostname {
    
    @GetMapping("/")
    public String DemoRestApi() {
    	String hostname = "hostname undefined";
		try {
			hostname = InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
    	
        return "Host: " + hostname;
    }
}
