/*
 * SecondView  2022-09-22
 *
 * Copyright (c) Pro Data GmbH & ASA KG. All rights reserved.
 */

package com.vaadin.starter.skeleton;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;

/**
 * SecondView
 * @author Hannes Tribus
 * @since 2022-09-22
 */
@Route("")
public class SecondView extends VerticalLayout {

	public SecondView() {
		// Use TextField for standard text input
		TextField textField = new TextField("Your name");
		textField.addThemeName("bordered");

		// Button click listeners can be defined as lambda expressions
		GreetService greetService = new GreetService();
		Button button = new Button("Say hello part 2",
			e -> Notification.show(greetService.greet(textField.getValue())));

		// Theme variants give you predefined extra styles for components.
		// Example: Primary button is more prominent look.
		button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

		// You can specify keyboard shortcuts for buttons.
		// Example: Pressing enter in this view clicks the Button.
		button.addClickShortcut(Key.ENTER);

		// Use custom CSS classes to apply styling. This is defined in shared-styles.css.
		addClassName("centered-content");

		add(textField, button);
	}
}
