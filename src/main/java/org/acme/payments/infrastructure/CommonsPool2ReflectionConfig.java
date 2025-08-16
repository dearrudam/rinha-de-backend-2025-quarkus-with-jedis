package org.acme.payments.infrastructure;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.apache.commons.pool2.impl.DefaultEvictionPolicy;

@RegisterForReflection(targets = DefaultEvictionPolicy.class)
public class CommonsPool2ReflectionConfig {
    // Classe utilitária para registrar DefaultEvictionPolicy para reflexão no build nativo
}

