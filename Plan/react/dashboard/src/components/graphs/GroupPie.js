import React, {useEffect} from 'react';
import {useTranslation} from "react-i18next";
import {useTheme} from "../../hooks/themeHook";
import {withReducedSaturation} from "../../util/colors";
import Accessibility from "highcharts/modules/accessibility";
import Highcharts from "highcharts";

const GroupPie = ({id, groups, colors, name}) => {
    const {t} = useTranslation();
    const {nightModeEnabled, graphTheming} = useTheme();

    useEffect(() => {
        const reduceColors = (colorsToReduce) => colorsToReduce.map(color => withReducedSaturation(color));

        function getColors() {
            const actualColors = colors ? colors : groups.map(group => group.color);
            return nightModeEnabled ? reduceColors(actualColors) : actualColors;
        }

        const series = groups.map(group => {
            return {name: t(group.name), y: group.y}
        });
        const pieSeries = {
            name: name,
            colorByPoint: true,
            colors: getColors(),
            data: series
        };

        Accessibility(Highcharts);
        Highcharts.setOptions(graphTheming);
        Highcharts.chart(id, {
            chart: {
                backgroundColor: 'transparent',
                plotBorderWidth: null,
                plotShadow: false,
                type: 'pie'
            },
            title: {text: ''},
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
                    return '<b>' + this.point.name + ':</b> ' + this.y;
                }
            },
            series: [pieSeries]
        });
    }, [id, colors, groups, name, graphTheming, nightModeEnabled, t]);

    return (<div className="chart-area" id={id}/>);
};

export default GroupPie;