import React, {useEffect} from 'react';
import {useTranslation} from "react-i18next";
import {useTheme} from "../../hooks/themeHook";
import {withReducedSaturation} from "../../util/colors";
import Highcharts from "highcharts";
import Accessibility from "highcharts/modules/accessibility";

const GeolocationBarGraph = ({series, color}) => {
    const {t} = useTranslation();
    const {nightModeEnabled, graphTheming} = useTheme();

    useEffect(() => {
        const bars = series.map(bar => bar.value);
        const categories = series.map(bar => bar.label);
        const geolocationBarSeries = {
            color: nightModeEnabled ? withReducedSaturation(color) : color,
            name: t('html.label.players'),
            data: bars
        };

        Accessibility(Highcharts);
        Highcharts.setOptions(graphTheming);
        Highcharts.chart("countryBarChart", {
            chart: {type: 'bar'},
            title: {text: ''},
            xAxis: {
                categories: categories,
                title: {text: ''}
            },
            yAxis: {
                min: 0,
                title: {text: t('html.label.players'), align: 'high'},
                labels: {overflow: 'justify'}
            },
            legend: {enabled: false},
            plotOptions: {
                bar: {
                    dataLabels: {enabled: true}
                }
            },
            series: [geolocationBarSeries]
        })
    }, [color, series, graphTheming, nightModeEnabled, t]);

    return (<div id="countryBarChart"/>);
};

export default GeolocationBarGraph