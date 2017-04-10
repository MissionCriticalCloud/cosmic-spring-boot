'use strict';

const app = {

    // Constants
    DECIMAL_FORMAT: '0,0.00',
    API_DATE_FORMAT: 'YYYY-MM-DD',
    MONTH_SELECTOR_FORMAT: 'YYYY-MM',
    SELECTED_MONTH_HUMAN_FORMAT: 'MMMM YYYY',
    USAGE_API_BASE_URL: undefined,
    GENERAL_USAGE_PATH: '/general?from={{& from }}&to={{& to }}&path={{& path }}',
    DEFAULT_ERROR_MESSAGE: 'Unable to communicate with the Usage API. Please contact your system administrator.',

    // Templates
    domainsListTemplate: '#ui-domains-list-template',
    printingHeadersTemplate: '#ui-printing-headers-template',
    errorMessageTemplate: '#ui-error-message-template',

    // Components
    errorMessageContainer: '#ui-error-message',
    monthSelectorComponent: '#ui-month-selector',
    domainPathField: '#ui-domain-path',
    cpuPriceField: '#ui-cpu-price',
    memoryPriceField: '#ui-memory-price',
    storagePriceField: '#ui-storage-price',
    ipAddressPriceField: '#ui-ip-address-price',
    generateReportButton: '#ui-generate-report-btn',
    printingHeadersContainer: '#ui-printing-headers',
    domainsTable: '#ui-domains-table',

    init: function(baseUrl) {
        this.USAGE_API_BASE_URL = baseUrl;

        numeral.defaultFormat(this.DECIMAL_FORMAT);
        _.bindAll(this, ... _.functions(this));

        $(this.monthSelectorComponent).datepicker('setDate', new Date());
        this.renderPrintingHeaders();
        this.renderDomainsList();

        $(this.generateReportButton).on('click', this.generateReportButtonOnClick);
    },

    renderPrintingHeaders: function() {
        var selectedMonth = $(this.monthSelectorComponent).datepicker('getFormattedDate');

        const selectedMonthFormatted = moment(selectedMonth, this.MONTH_SELECTOR_FORMAT)
            .format(this.SELECTED_MONTH_HUMAN_FORMAT);

        const cpuPriceFormatted = numeral($(this.cpuPriceField).val()).format();
        const memoryPriceFormatted = numeral($(this.memoryPriceField).val()).format();
        const storagePriceFormatted = numeral($(this.storagePriceField).val()).format();
        const ipAddressPriceFormatted = numeral($(this.ipAddressPriceField).val()).format();

        const html = $(this.printingHeadersTemplate).html();
        const rendered = Mustache.render(html, {
            selectedMonth: selectedMonthFormatted,
            cpuPrice: cpuPriceFormatted,
            memoryPrice: memoryPriceFormatted,
            storagePrice: storagePriceFormatted,
            ipAddressPrice: ipAddressPriceFormatted
        });
        $(this.printingHeadersContainer).html(rendered);
    },

    renderDomainsList: function(domains) {
        const html = $(this.domainsListTemplate).html();
        const rendered = Mustache.render(html, { domains: domains });
        $('tbody', this.domainsTable).html(rendered);
    },

    generateReportButtonOnClick: function(event) {
        event.preventDefault();

        const selectedMonth = $(this.monthSelectorComponent).datepicker('getFormattedDate');

        const from = moment(selectedMonth, this.MONTH_SELECTOR_FORMAT)
            .format(this.API_DATE_FORMAT);

        const to = moment(selectedMonth, this.MONTH_SELECTOR_FORMAT)
            .add(1, 'months')
            .format(this.API_DATE_FORMAT);

        const path = $(this.domainPathField).val();

        const renderedUrl = Mustache.render(this.USAGE_API_BASE_URL + this.GENERAL_USAGE_PATH, {
            from: from,
            to: to,
            path: path
        });

        $.get(renderedUrl, this.parseDomainsResult)
            .fail(this.parseErrorResponse);
    },

    parseDomainsResult: function(domains) {
        this.renderPrintingHeaders();
        this.calculateDomainsCosts(domains);
        this.renderDomainsList(domains);
    },

    parseErrorResponse: function(response) {
        if (response.status >= 200 && response.status < 600) {
            try {
                console.log(JSON.parse(response.responseText));
            } catch (e) {
                console.log('Unable to parse error response.');
            }
        }
        this.renderErrorMessage(this.DEFAULT_ERROR_MESSAGE);
    },

    calculateDomainsCosts: function(domains) {
        _.each(domains, this.calculateDomainCosts);
    },

    calculateDomainCosts: function(domain) {
        const cpuPrice = numeral($(this.cpuPriceField).val());
        const memoryPrice = numeral($(this.memoryPriceField).val());
        const storagePrice = numeral($(this.storagePriceField).val());
        const ipAddressPrice = numeral($(this.ipAddressPriceField).val());

        domain.costs = {
            compute: {
                cpu: numeral(cpuPrice.value()).multiply(domain.compute.cpu),
                memory: numeral(memoryPrice.value()).multiply(domain.compute.memory)
            },
            storage: numeral(storagePrice.value()).multiply(domain.storage),
            network: {
                ipAddresses: numeral(ipAddressPrice.value()).multiply(domain.network.ipAddresses)
            }
        };

        domain.costs.total = numeral(domain.costs.compute.cpu.value())
                              .add(domain.costs.compute.memory.value())
                              .add(domain.costs.storage.value())
                              .add(domain.costs.network.ipAddresses.value());

        domain.compute.cpu = numeral(domain.compute.cpu).format();
        domain.compute.memory = numeral(domain.compute.memory).format();
        domain.storage = numeral(domain.storage).format();
        domain.network.ipAddresses = numeral(domain.network.ipAddresses).format();

        domain.costs.compute.cpu = domain.costs.compute.cpu.format();
        domain.costs.compute.memory = domain.costs.compute.memory.format();
        domain.costs.storage = domain.storage.format();
        domain.costs.network.ipAddresses = domain.costs.network.ipAddresses.format();

        domain.costs.total = domain.costs.total.format();
    },

    renderErrorMessage: function(errorMessage) {
        const html = $(this.errorMessageTemplate).html();
        const rendered = Mustache.render(html, { errorMessage: errorMessage });
        $(this.errorMessageContainer).html(rendered);
    }

};
