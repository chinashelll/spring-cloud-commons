package org.springframework.cloud.client.serviceregistry;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.PreDestroy;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.cloud.client.discovery.ManagementServerPortUtils;
import org.springframework.cloud.client.discovery.event.InstanceRegisteredEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

/**
 * Lifecycle methods that may be useful and common to {@link ServiceRegistry}
 * implementations.
 *
 * TODO: document the lifecycle
 *
 * @param <R> registration type passed to the {@link ServiceRegistry}.
 *
 * @author Spencer Gibb
 */
public abstract class AbstractAutoServiceRegistration<R extends Registration>
		implements AutoServiceRegistration, ApplicationContextAware {
	private static final Log logger = LogFactory
			.getLog(AbstractAutoServiceRegistration.class);

	private boolean autoStartup = true;

	private AtomicBoolean running = new AtomicBoolean(false);

	private int order = 0;

	private ApplicationContext context;

	private Environment environment;

	private AtomicInteger port = new AtomicInteger(0);

	protected ApplicationContext getContext() {
		return context;
	}

	@EventListener(WebServerInitializedEvent.class)
	public void bind(WebServerInitializedEvent event) {
		ApplicationContext context = event.getApplicationContext();
		if (context instanceof ServletWebServerApplicationContext) {
			if ("management".equals(
					((ServletWebServerApplicationContext) context).getNamespace())) {
				return;
			}
		}
		this.port.compareAndSet(0, event.getWebServer().getPort());
		this.start();
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext)
			throws BeansException {
		this.context = applicationContext;
		this.environment = this.context.getEnvironment();
	}

	@Deprecated
	protected Environment getEnvironment() {
		return environment;
	}

	@Deprecated
	protected AtomicInteger getPort() {
		return port;
	}

	public boolean isAutoStartup() {
		return this.autoStartup;
	}

	public void start() {
		if (!isEnabled()) {
			if (logger.isDebugEnabled()) {
				logger.debug("Discovery Lifecycle disabled. Not starting");
			}
			return;
		}

		// only initialize if nonSecurePort is greater than 0 and it isn't already running
		// because of containerPortInitializer below
		if (!this.running.get()) {
			register();
			if (shouldRegisterManagement()) {
				registerManagement();
			}
			this.context.publishEvent(
					new InstanceRegisteredEvent<>(this, getConfiguration()));
			this.running.compareAndSet(false, true);
		}

	}

	/**
	 * @return if the management service should be registered with the
	 * {@link ServiceRegistry}
	 */
	protected boolean shouldRegisterManagement() {
		return getManagementPort() != null
				&& ManagementServerPortUtils.isDifferent(this.context);
	}

	/**
	 * @return the object used to configure the registration
	 */
	@Deprecated
	protected abstract Object getConfiguration();

	/**
	 * @return true, if this is enabled
	 */
	protected abstract boolean isEnabled();

	/**
	 * @return the serviceId of the Management Service
	 */
	@Deprecated
	protected String getManagementServiceId() {
		// TODO: configurable management suffix
		return this.context.getId() + ":management";
	}

	/**
	 * @return the service name of the Management Service
	 */
	@Deprecated
	protected String getManagementServiceName() {
		// TODO: configurable management suffix
		return getAppName() + ":management";
	}

	/**
	 * @return the management server port
	 */
	@Deprecated
	protected Integer getManagementPort() {
		return ManagementServerPortUtils.getPort(this.context);
	}

	/**
	 * @return the app name, currently the spring.application.name property
	 */
	@Deprecated
	protected String getAppName() {
		return this.environment.getProperty("spring.application.name", "application");
	}

	@PreDestroy
	public void destroy() {
		stop();
	}

	public boolean isRunning() {
		return this.running.get();
	}

	protected AtomicBoolean getRunning() {
		return running;
	}

	public int getOrder() {
		return this.order;
	}

	public int getPhase() {
		return 0;
	}

	private ServiceRegistry<R> serviceRegistry;

	protected AbstractAutoServiceRegistration(ServiceRegistry<R> serviceRegistry) {
		this.serviceRegistry = serviceRegistry;
	}

	protected ServiceRegistry<R> getServiceRegistry() {
		return this.serviceRegistry;
	}

	protected abstract R getRegistration();

	protected abstract R getManagementRegistration();

	/**
	 * Register the local service with the {@link ServiceRegistry}
	 */
	protected void register() {
		this.serviceRegistry.register(getRegistration());
	}

	/**
	 * Register the local management service with the {@link ServiceRegistry}
	 */
	protected void registerManagement() {
		R registration = getManagementRegistration();
		if (registration != null) {
			this.serviceRegistry.register(registration);
		}
	}

	/**
	 * De-register the local service with the {@link ServiceRegistry}
	 */
	protected void deregister() {
		this.serviceRegistry.deregister(getRegistration());
	}

	/**
	 * De-register the local management service with the {@link ServiceRegistry}
	 */
	protected void deregisterManagement() {
		R registration = getManagementRegistration();
		if (registration != null) {
			this.serviceRegistry.deregister(registration);
		}
	}

	public void stop() {
		if (this.getRunning().compareAndSet(true, false) && isEnabled()) {
			deregister();
			if (shouldRegisterManagement()) {
				deregisterManagement();
			}
			this.serviceRegistry.close();
		}
	}

}
