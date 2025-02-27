import React, {useEffect} from "react";
import Highcharts from 'highcharts';
import factory from 'highcharts/modules/drilldown';

import {formatTimeAmount} from '../../util/formatters'
import {useTheme} from "../../hooks/themeHook";
import {withReducedSaturation} from "../../util/colors";
import {useMetadata} from "../../hooks/metadataHook";
import {useTranslation} from "react-i18next";
import Accessibility from "highcharts/modules/accessibility";

const WorldPie = ({id, worldSeries, gmSeries}) => {
    const {t} = useTranslation();
    const {gmPieColors} = useMetadata();

    useEffect(() => {
        factory(Highcharts)
    }, []);

    const {nightModeEnabled, graphTheming} = useTheme();

    useEffect(() => {
        const reduceColors = (series) => {
            return series.map(slice => {
                return {...slice, color: withReducedSaturation(slice.color)};
            })
        }

        const pieSeries = {
            name: t('html.label.worldPlaytime'),
            colorByPoint: true,
            data: nightModeEnabled ? reduceColors(worldSeries) : worldSeries
        };

        const defaultTitle = '';
        const defaultSubtitle = t('html.text.clickToExpand');
        Accessibility(Highcharts);
        Highcharts.setOptions(graphTheming);
        setTimeout(() => {
            const chart = Highcharts.chart(id, {
                chart: {
                    backgroundColor: 'transparent',
                    plotBackgroundColor: 'transparent',
                    plotBorderWidth: null,
                    plotShadow: false,
                    type: 'pie',
                    events: {
                        drilldown: function (e) {
                            chart.setTitle({text: '' + e.point.name}, {text: ''});
                        },
                        drillup: function () {
                            chart.setTitle({text: defaultTitle}, {text: defaultSubtitle});
                        }
                    }
                },
                title: {text: defaultTitle},
                subtitle: {
                    text: defaultSubtitle
                },
                plotOptions: {
                    pie: {
                        allowPointSelect: true,
                        cursor: 'pointer',
                        dataLabels: {
                            enabled: false
                        },
                        showInLegend: true
                    }
                },
                tooltip: {
                    formatter: function () {
                        return '<b>' + this.point.name + ':</b> ' + formatTimeAmount(this.y) + ' (' + this.percentage.toFixed(2) + '%)';
                    }
                },
                series: [pieSeries],
                drilldown: {
                    series: gmSeries.map(function (d) {
                        return {name: d.name, id: d.id, colors: gmPieColors, data: d.data}
                    })
                }
            });
        }, 25)
    }, [worldSeries, gmSeries, graphTheming, nightModeEnabled, id, gmPieColors, t]);

    return (<div className="chart-pie" id={id}/>)
}

export default WorldPie;