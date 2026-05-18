package com.ajinkyagokhale.espflasher;

import com.ajinkyagokhale.espflasher.ui.FlasherApp;
import javafx.application.Application;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EspflasherApplication {

	public static void main(String[] args) {
		Application.launch(FlasherApp.class, args);
	}

}