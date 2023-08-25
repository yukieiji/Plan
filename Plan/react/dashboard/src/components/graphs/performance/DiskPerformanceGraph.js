import React, {useEffect} from 'react';

import {linegraphButtons, tooltip} from "../../../util/graphs";
import Highcharts from "highcharts/highstock";
import NoDataDisplay from "highcharts/modules/no-data-to-display"
import {useTranslation} from "react-i18next";
import {useTheme} from "../../../hooks/themeHook";
import {withReducedSaturation} from "../../../util/colors";
import Accessibility from "highcharts/modules/accessibility";
import {useMetadata} from "../../../hooks/metadataHook";

const DiskPerformanceGraph = ({id, data, dataSeries}) => {
    const {t} = useTranslation();
    const {graphTheming, nightModeEnabled} = useTheme();
    const {timeZoneOffsetMinutes} = useMetadata();

    useEffect(() => {
        const zones = {
            disk: [{
                value: data.zones.diskThresholdMed,
                color: data.colors.low
            }, {
                value: data.zones.diskThresholdHigh,
                color: data.colors.med
            }, {
                value: Number.MAX_VALUE,
                color: data.colors.high
            }]
        };

        const series = {
            disk: {
                name: t('html.label.disk'),
                type: 'areaspline',
                color: nightModeEnabled ? withReducedSaturation(data.colors.high) : data.colors.high,
                zones: zones.disk,
                tooltip: tooltip.zeroDecimals,
                data: dataSeries.disk
            }
        };

        NoDataDisplay(Highcharts);
        Accessibility(Highcharts);
        Highcharts.setOptions({lang: {noData: t('html.label.noDataToDisplay')}})
        Highcharts.setOptions(graphTheming);
        Highcharts.stockChart(id, {
            rangeSelector: {
                selected: 2,
                buttons: linegraphButtons
            },
            yAxis: {
                labels: {
                    formatter: function () {
                        return this.value + ' MB';
                    }
                },
                softMin: 0
            },
            title: {text: ''},
            plotOptions: {
                areaspline: {
                    fillOpacity: nightModeEnabled ? 0.2 : 0.4
                }
            },
            legend: {
                enabled: true
            },
            time: {
                timezoneOffset: timeZoneOffsetMinutes
            },
            series: [series.disk]
        });
    }, [data, dataSeries, graphTheming, nightModeEnabled, id, t, timeZoneOffsetMinutes])

    return (
        <div className="chart-area" style={{height: "450px"}} id={id}>
            <span className="loader"/>
        </div>
    )
};

export default DiskPerformanceGraph