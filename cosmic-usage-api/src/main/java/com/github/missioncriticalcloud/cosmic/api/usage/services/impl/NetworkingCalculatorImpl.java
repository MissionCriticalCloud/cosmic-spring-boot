package com.github.missioncriticalcloud.cosmic.api.usage.services.impl;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import com.github.missioncriticalcloud.cosmic.api.usage.services.AggregationCalculator;
import com.github.missioncriticalcloud.cosmic.usage.core.model.DataUnit;
import com.github.missioncriticalcloud.cosmic.usage.core.model.Domain;
import com.github.missioncriticalcloud.cosmic.usage.core.model.Network;
import com.github.missioncriticalcloud.cosmic.usage.core.model.Networking;
import com.github.missioncriticalcloud.cosmic.usage.core.model.PublicIp;
import com.github.missioncriticalcloud.cosmic.usage.core.model.TimeUnit;
import com.github.missioncriticalcloud.cosmic.usage.core.model.aggregations.DomainAggregation;
import com.github.missioncriticalcloud.cosmic.usage.core.repositories.PublicIpsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("networkingCalculator")
public class NetworkingCalculatorImpl implements AggregationCalculator<DomainAggregation> {

    private final PublicIpsRepository publicIpsRepository;

    @Autowired
    public NetworkingCalculatorImpl(final PublicIpsRepository publicIpsRepository) {
        this.publicIpsRepository = publicIpsRepository;
    }

    @Override
    public void calculateAndMerge(
            final Domain domain,
            final BigDecimal secondsPerSample,
            final DataUnit dataUnit,
            final TimeUnit timeUnit,
            final DomainAggregation aggregation
    ) {
        final Networking networking = domain.getUsage().getNetworking();
        final Map<String, Network> networksMap = new HashMap<>();

        aggregation.getPublicIpAggregations().forEach(publicIpAggregation -> {
            final BigDecimal duration = timeUnit.convert(publicIpAggregation.getCount().multiply(secondsPerSample));
            final PublicIp publicIp = publicIpsRepository.get(publicIpAggregation.getUuid());

            if (publicIp == null) {
                return;
            }

            publicIp.setDuration(duration);

            final Network publicIpNetwork = publicIp.getNetwork();
            final Network network = networksMap.getOrDefault(publicIpNetwork.getUuid(), publicIpNetwork);

            network.getPublicIps().add(publicIp);
            networksMap.put(network.getUuid(), network);
        });

        networking.getNetworks().addAll(networksMap.values());
    }

}
