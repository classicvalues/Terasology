// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.engine.core.subsystem.common;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.jmx.JmxConfig;
import io.micrometer.jmx.JmxMeterRegistry;
import org.terasology.engine.config.SystemConfig;
import org.terasology.engine.context.Context;
import org.terasology.engine.core.GameEngine;
import org.terasology.engine.core.Time;
import org.terasology.engine.core.subsystem.EngineSubsystem;
import org.terasology.engine.monitoring.gui.AdvancedMonitor;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

public class MonitoringSubsystem implements EngineSubsystem {

    private AdvancedMonitor advancedMonitor;

    @Override
    public String getName() {
        return "Monitoring";
    }

    @Override
    public void initialise(GameEngine engine, Context rootContext) {
        if (rootContext.get(SystemConfig.class).monitoringEnabled.get()) {
            advancedMonitor = new AdvancedMonitor();
            advancedMonitor.setVisible(true);
        }

        initMetrics(rootContext.get(Time.class));
    }

    private void initMetrics(Time time) {
        MeterRegistry meterRegistry = Metrics.globalRegistry;
        // Install all the micrometer built-in metrics on the global micrometer registry.
        new ClassLoaderMetrics().bindTo(meterRegistry);
        new JvmMemoryMetrics().bindTo(meterRegistry);
        new JvmGcMetrics().bindTo(meterRegistry);
        new JvmThreadMetrics().bindTo(meterRegistry);
        new ProcessorMetrics().bindTo(meterRegistry);

        // Install Reactor metrics. In theory. I haven't seen them show up yet.
        Schedulers.enableMetrics();

        // Add one of our own.
        Gauge.builder("terasology.fps", time::getFps)
                .description("framerate")
                .baseUnit("frames/sec")
                .register(meterRegistry);

        // Make global registry available via JMX.
        JmxMeterRegistry jmxMeterRegistry = new JmxMeterRegistry(new JmxConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public Duration step() {
                return Duration.ofSeconds(5);  // default was 1 minute
            }
        }, Clock.SYSTEM);
        Metrics.addRegistry(jmxMeterRegistry);

        // If we want to make global metrics available to our custom view,
        // we add our custom registry to the global composite:
        //
        // Metrics.addRegistry(DebugOverlay.meterRegistry);
    }

    @Override
    public void shutdown() {
        if (advancedMonitor != null) {
            advancedMonitor.close();
        }
    }
}
