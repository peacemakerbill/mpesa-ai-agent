package com.darajaagentx.api.devtunnel;

import com.darajaagentx.api.config.AppEnvironment;
import com.darajaagentx.api.service.BusinessOperationService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.core.env.Environment;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/**
 * On a developer's machine, when no public address is set for Safaricom's STK Push answers, opens a zrok2 or ngrok tunnel once the service is
 * up and gives its address to the business operations. It runs in the background, so the service never waits for it, and closes the tunnel
 * when the service stops. In staging and production it does nothing. See docs/DEV_TUNNEL.md.
 */
@Component
class DevTunnelStarter {

    private static final Logger log = LoggerFactory.getLogger(DevTunnelStarter.class);

    private final DevTunnelProperties props;
    private final Environment environment;
    private final BusinessOperationService operations;
    private final AtomicReference<Launcher.Running> tunnel = new AtomicReference<>();

    DevTunnelStarter(DevTunnelProperties props, Environment environment, BusinessOperationService operations) {
        this.props = props;
        this.environment = environment;
        this.operations = operations;
    }

    @EventListener(ApplicationReadyEvent.class)
    void onReady() {
        Platform platform = Platform.current();
        Path home = Path.of(System.getProperty("user.home"));
        Path installFolder = props.binDir().isBlank() ? home.resolve(".agentx").resolve("bin") : Path.of(props.binDir());
        Path state = Path.of(System.getProperty("java.io.tmpdir")).resolve("agentx-dev-tunnel");
        DevTunnel dev = new DevTunnel(props, platform, ToolLocator.system(platform, installFolder), new ProcessLauncher(),
                new ToolInstaller(platform, props, ToolInstaller::fetch), DevTunnel.httpReader(), home, installFolder, state);

        DevTunnelPlanner.Plan plan = DevTunnelPlanner.decide(new DevTunnelPlanner.Facts(
                AppEnvironment.parse(environment.getProperty("agentx.env", "development")),
                props.off(),
                environment.getProperty("agentx.stk.callback-url", ""),
                environment.getProperty("agentx.public-base-url", ""),
                props.provider(),
                dev.installed(Provider.ZROK), dev.ready(Provider.ZROK), dev.installed(Provider.NGROK), dev.ready(Provider.NGROK)));

        switch (plan.action()) {
            case START -> {
                log.info("Dev tunnel: {}", plan.reason());
                int port = environment.getProperty("local.server.port", Integer.class, environment.getProperty("server.port", Integer.class, 8084));
                Thread worker = new Thread(() -> open(dev, plan.provider(), port), "dev-tunnel");
                worker.setDaemon(true);
                worker.start();
            }
            case NO_CREDENTIALS -> log.info("Dev tunnel: {}", plan.reason());
            default -> log.debug("Dev tunnel not needed: {}", plan.reason());
        }
    }

    private void open(DevTunnel dev, Provider provider, int port) {
        try {
            DevTunnel.Open open = dev.open(provider, port);
            tunnel.set(open.running());
            operations.setStkCallbackBase(open.url());
            log.info("Dev tunnel ready: {}. Check it with {}/daraja/ping", open.url(), open.url());
        } catch (IOException | RuntimeException e) {
            log.warn("Dev tunnel could not be opened: {}. The service runs without it, and STK results are fetched from Safaricom about every minute instead.", e.getMessage());
        }
    }

    @PreDestroy
    void close() {
        Launcher.Running running = tunnel.getAndSet(null);
        if (running != null) {
            running.stop();
            log.info("Dev tunnel closed");
        }
    }
}
