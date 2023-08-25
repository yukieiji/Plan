import {Card} from "react-bootstrap";
import React from "react";
import {CardLoader} from "../../navigation/Loader";
import ServerPie from "../../graphs/ServerPie";
import {faNetworkWired} from "@fortawesome/free-solid-svg-icons";
import CardHeader from "../CardHeader";
import {useDataRequest} from "../../../hooks/dataFetchHook";
import {fetchServerPie} from "../../../service/networkService";
import {ErrorViewCard} from "../../../views/ErrorView";

const ServerPieCard = () => {
    const {data, loadingError} = useDataRequest(fetchServerPie, []);

    if (!data) return <CardLoader/>;
    if (loadingError) return <ErrorViewCard error={loadingError}/>;

    const series = data.server_pie_series_30d;
    const colors = data.server_pie_colors;

    return (
        <Card>
            <CardHeader icon={faNetworkWired} color={'teal'} label={'html.label.serverPlaytime30days'}/>
            <ServerPie series={series} colors={colors}/>
        </Card>
    )
}

export default ServerPieCard;