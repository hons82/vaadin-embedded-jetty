/*
 * FirstVaadinServlet  2022-09-22
 *
 * Copyright (c) Pro Data GmbH & ASA KG. All rights reserved.
 */

package com.vaadin.starter.skeleton;

import com.vaadin.flow.router.RouteConfiguration;
import com.vaadin.flow.server.VaadinServlet;

/**
 * FirstVaadinServlet
 * @author Hannes Tribus
 * @since 2022-09-22
 */
public class FirstVaadinServlet extends VaadinServlet {
	@Override
	protected void servletInitialized() {
		RouteConfiguration routeConfig = RouteConfiguration.forApplicationScope();
		routeConfig.update(() -> routeConfig.setAnnotatedRoute(FirstView.class));
	}
}
