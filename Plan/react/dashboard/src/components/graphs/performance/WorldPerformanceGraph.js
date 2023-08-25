import React, {useEffect} from 'react';

import {linegraphButtons, tooltip} from "../../../util/graphs";
import Highcharts from "highcharts/highstock";
import NoDataDisplay from "highcharts/modules/no-data-to-display"
import {useTranslation} from "react-i18next";
import {useTheme} from "../../../hooks/themeHook";
import {withReducedSaturation} from "../../../util/colors";
import Accessibility from "highcharts/modules/accessibility";
import {useMetadata} from "../../../hooks/metadataHook";

const WorldPerformanceGraph = ({id, data, dataSeries}) => {
    const {t} = useTranslation();
    const {graphTheming, nightModeEnabled} = useTheme();
    const {timeZoneOffsetMinutes} = useMetadata();

    useEffect(() => {
        const spline = 'spline'

        const series = {
            playersOnline: {
                name: t('html.label.playersOnline'),
                type: 'areaspline',
                tooltip: tooltip.zeroDecimals,
                data: dataSeries.playersOnline,
                color: data.colors.playersOnline,
                yAxis: 0
            }, entities: {
                name: t('html.label.loadedEntities'),
                type: spline,
                tooltip: tooltip.zeroDecimals,
                data: dataSeries.entities,
                color: nightModeEnabled ? withReducedSaturation(data.colors.entities) : data.colors.entities,
                yAxis: 1
            }, chunks: {
                name: t('html.label.loadedChunks'),
                type: spline,
                tooltip: tooltip.zeroDecimals,
                data: dataSeries.chunks,
                color: nightModeEnabled ? withReducedSaturation(data.colors.chunks) : data.colors.chunks,
                yAxis: 2
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
            yAxis: [{
                labels: {
                    formatter: function () {
                        return this.value + ' ' + t('html.unit.players')
                    }
                },
                softMin: 0,
                softMax: 2
            }, {
                labels: {
                    formatter: function () {
                        return this.value + ' ' + t('html.label.entities')
                    }
                },
                softMin: 0,
            }, {
                labels: {
                    formatter: function () {
                        return this.value + ' ' + t('html.unit.chunks')
                    }
                },
                softMin: 0,
            }],
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
            series: [series.playersOnline, series.entities, series.chunks]
        });
    }, [data, dataSeries, graphTheming, nightModeEnabled, id, t, timeZoneOffsetMinutes])

    return (
        <div className="chart-area" style={{height: "450px"}} id={id}>
            <span className="loader"/>
        </div>
    )
};

export default WorldPerformanceGraph