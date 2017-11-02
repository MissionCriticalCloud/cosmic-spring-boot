package com.github.missioncriticalcloud.cosmic.api.usage.services.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.github.missioncriticalcloud.cosmic.api.usage.services.AggregationCalculator;
import com.github.missioncriticalcloud.cosmic.api.usage.services.UsageCalculator;
import com.github.missioncriticalcloud.cosmic.usage.core.exceptions.NoMetricsFoundException;
import com.github.missioncriticalcloud.cosmic.usage.core.model.DataUnit;
import com.github.missioncriticalcloud.cosmic.usage.core.model.Domain;
import com.github.missioncriticalcloud.cosmic.usage.core.model.TimeUnit;
import com.github.missioncriticalcloud.cosmic.usage.core.model.aggregations.DomainAggregation;
import com.github.missioncriticalcloud.cosmic.usage.core.repositories.DomainsRepository;
import com.github.missioncriticalcloud.cosmic.usage.core.repositories.MetricsRepository;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.support.CronSequenceGenerator;
import org.springframework.stereotype.Service;

@Service
public class UsageCalculatorImpl implements UsageCalculator {
    private final DomainsRepository domainsRepository;

    private final MetricsRepository computeRepository;
    private final MetricsRepository storageRepository;
    private final MetricsRepository networkingRepository;

    private final AggregationCalculator<DomainAggregation> computeCalculator;
    private final AggregationCalculator<DomainAggregation> storageCalculator;
    private final AggregationCalculator<DomainAggregation> networkingCalculator;

    private final String scanInterval;

    @Autowired
    public UsageCalculatorImpl(
            final DomainsRepository domainsRepository,
            @Qualifier("computeRepository") final MetricsRepository computeRepository,
            @Qualifier("storageRepository") final MetricsRepository storageRepository,
            @Qualifier("networkingRepository") final MetricsRepository networkingRepository,
            @Qualifier("computeCalculator") final AggregationCalculator<DomainAggregation> computeCalculator,
            @Qualifier("storageCalculator") final AggregationCalculator<DomainAggregation> storageCalculator,
            @Qualifier("networkingCalculator") final AggregationCalculator<DomainAggregation> networkingCalculator,
            @Value("${cosmic.usage-api.scan-interval}") final String scanInterval
    ) {
        this.domainsRepository = domainsRepository;

        this.computeRepository = computeRepository;
        this.storageRepository = storageRepository;
        this.networkingRepository = networkingRepository;

        this.computeCalculator = computeCalculator;
        this.storageCalculator = storageCalculator;
        this.networkingCalculator = networkingCalculator;

        this.scanInterval = scanInterval;
    }

    @Override
    public Domain calculate(
            final DateTime from,
            final DateTime to,
            final String path,
            final DataUnit dataUnit,
            final TimeUnit timeUnit) {
        final Domain domain = domainsRepository.getByPath(path);
        final String domainUuid = domain.getUuid();

        final List<DomainAggregation> computeDomainAggregations = computeRepository.list(domainUuid, from, to);
        final List<DomainAggregation> storageDomainAggregations = storageRepository.list(domainUuid, from, to);
        final List<DomainAggregation> networkingDomainAggregations = networkingRepository.list(domainUuid, from, to);

        final BigDecimal secondsPerSample = calculateSecondsPerSample();

        computeCalculator.calculateAndMerge(domain, secondsPerSample, dataUnit, timeUnit, computeDomainAggregations);
        storageCalculator.calculateAndMerge(domain, secondsPerSample, dataUnit, timeUnit, storageDomainAggregations);
        networkingCalculator.calculateAndMerge(domain, secondsPerSample, dataUnit, timeUnit, networkingDomainAggregations);

        if (domain.getUsage().isEmpty()) {
            throw new NoMetricsFoundException();
        }

        return domain;
    }

    @Override
    public List<Domain> calculateComputeDomains(
            final DateTime from,
            final DateTime to,
            final String path,
            final DataUnit dataUnit,
            final TimeUnit timeUnit
    ) {

        final Map<String, Domain> domains = domainsRepository.map(path);
        domains.values().forEach(domain -> calculateCompute(from, to, dataUnit, timeUnit, domain));

        removeDomainsWithoutUsage(domains);
        if (domains.isEmpty()) {
            throw new NoMetricsFoundException();
        }

        return new ArrayList<>(domains.values());
    }

    @Override
    public Domain calculateComputeForUuid(
            final DateTime from,
            final DateTime to,
            final Domain domain,
            final DataUnit dataUnit,
            final TimeUnit timeUnit) {

        calculateCompute(from, to, dataUnit, timeUnit, domain);

        if (domain.getUsage().isEmpty()) {
            throw new NoMetricsFoundException();
        }

        return domain;
    }

    private void calculateCompute(
            final DateTime from,
            final DateTime to,
            final DataUnit dataUnit,
            final TimeUnit timeUnit,
            final Domain domain) {
        final BigDecimal secondsPerSample = calculateSecondsPerSample();

        final String domainUuid = domain.getUuid();

        final List<DomainAggregation> computeDomainAggregations = computeRepository.list(domainUuid, from, to);

        computeCalculator.calculateAndMerge(domain, secondsPerSample, dataUnit, timeUnit, computeDomainAggregations);
    }

    @Override
    public List<Domain> calculateStorageDomains(
            final DateTime from,
            final DateTime to,
            final String path,
            final DataUnit dataUnit,
            final TimeUnit timeUnit
    ) {

        final Map<String, Domain> domains = domainsRepository.map(path);
        domains.values().forEach(domain -> calculateStorage(from, to, dataUnit, timeUnit, domain));

        removeDomainsWithoutUsage(domains);
        if (domains.isEmpty()) {
            throw new NoMetricsFoundException();
        }

        return new ArrayList<>(domains.values());
    }

    @Override
    public Domain calculateStorageForUuid(
            final DateTime from,
            final DateTime to,
            final Domain domain,
            final DataUnit dataUnit,
            final TimeUnit timeUnit) {

        calculateStorage(from, to, dataUnit, timeUnit, domain);

        if (domain.getUsage().isEmpty()) {
            throw new NoMetricsFoundException();
        }

        return domain;
    }

    private void calculateStorage(
            final DateTime from,
            final DateTime to,
            final DataUnit dataUnit,
            final TimeUnit timeUnit,
            final Domain domain) {
        final BigDecimal secondsPerSample = calculateSecondsPerSample();

        final String domainUuid = domain.getUuid();

        final List<DomainAggregation> storageDomainAggregations = storageRepository.list(domainUuid, from, to);

        storageCalculator.calculateAndMerge(domain, secondsPerSample, dataUnit, timeUnit, storageDomainAggregations);
    }

    private BigDecimal calculateSecondsPerSample() {
        final CronSequenceGenerator cronSequence = new CronSequenceGenerator(scanInterval);
        final Date nextOccurrence = cronSequence.next(new Date());
        final Date followingOccurrence = cronSequence.next(nextOccurrence);

        final Duration interval = new Duration(nextOccurrence.getTime(), followingOccurrence.getTime());

        return BigDecimal.valueOf(interval.getStandardSeconds());
    }

    private void removeDomainsWithoutUsage(final Map<String, Domain> domainsMap) {
        final Set<String> uuidsToRemove = new HashSet<>();
        domainsMap.forEach((uuid, domain) -> {
            if (domain.getUsage().isEmpty()) {
                uuidsToRemove.add(uuid);
            }
        });
        uuidsToRemove.forEach(domainsMap::remove);
    }

}
