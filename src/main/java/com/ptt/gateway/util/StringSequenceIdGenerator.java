package com.ptt.gateway.util;

import org.hibernate.HibernateException;
import org.hibernate.MappingException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.Type;

import java.io.Serializable;
import java.util.Properties;
import java.util.stream.Stream;

public class StringSequenceIdGenerator implements IdentifierGenerator {

    public static final String SEQUENCE_PREFIX = "sequence_prefix";
    private String sequencePrefix;

    @Override
    public void configure(Type type, Properties params, ServiceRegistry serviceRegistry) throws MappingException {
        sequencePrefix = params.getProperty(SEQUENCE_PREFIX);
    }

    @Override
    public Serializable generate(SharedSessionContractImplementor session, Object object) throws HibernateException {
        String entityName = object.getClass().getName();
        String query = String.format("select %s from %s",
                session.getEntityPersister(entityName, object).getIdentifierPropertyName(),
                entityName);

        Stream<String> ids = session.createSelectionQuery(query, String.class).stream();

        Long max = ids.map(o -> o.replace(sequencePrefix, ""))
                .mapToLong(Long::parseLong)
                .max()
                .orElse(0L);

        return sequencePrefix + String.format("%04d", max + 1);
    }
}
