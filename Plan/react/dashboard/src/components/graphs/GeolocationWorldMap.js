import React, {useEffect} from 'react';
import {useTranslation} from "react-i18next";
import {useTheme} from "../../hooks/themeHook";
import Highcharts from 'highcharts/highmaps.js';
import topology from '@highcharts/map-collection/custom/world.topo.json';
import Accessibility from "highcharts/modules/accessibility";
import NoDataDisplay from "highcharts/modules/no-data-to-display";

export const ProjectionOptions = {
    MILLER: "html.label.geoProjection.miller",
    MERCATOR: "html.label.geoProjection.mercator",
    EQUAL_EARTH: "html.label.geoProjection.equalEarth"
    // ORTOGRAPHIC: "html.label.geoProjection.ortographic"
}

const getProjection = option => {
    switch (option) {
        case ProjectionOptions.MERCATOR:
            return {name: 'WebMercator'};
        case ProjectionOptions.EQUAL_EARTH:
            return {name: 'EqualEarth'};
        // Ortographic projection stops working after a while for some reason
        // case ProjectionOptions.ORTOGRAPHIC:
        //     return {name: 'Orthographic'};
        case ProjectionOptions.MILLER:
        default:
            return {name: 'Miller'};
    }
}

const GeolocationWorldMap = ({series, colors, projection}) => {
    const {t} = useTranslation();
    const {nightModeEnabled, graphTheming} = useTheme();

    useEffect(() => {
        const mapSeries = {
            name: t('html.label.players'),
            type: 'map',
            data: series,
            joinBy: ['iso-a3', 'code']
        };

        NoDataDisplay(Highcharts);
        Accessibility(Highcharts);
        Highcharts.setOptions(graphTheming);
        Highcharts.setOptions({lang: {noData: t('html.label.noDataToDisplay')}});
        Highcharts.mapChart('countryWorldMap', {
            chart: {
                map: topology,
                animation: true
            },
            title: {text: ''},

            mapNavigation: {
                enabled: true,
                enableDoubleClickZoomTo: true,
                enableMouseWheelZoom: true,
                enableTouchZoom: true
            },

            mapView: {
                projection: getProjection(projection)
            },

            colorAxis: {
                min: 1,
                type: 'logarithmic',
                minColor: colors.low,
                maxColor: colors.high
            },
            series: [mapSeries]
        })
    }, [colors, series, graphTheming, nightModeEnabled, t, projection]);

    return (<div id="countryWorldMap"/>);
};

export default GeolocationWorldMap