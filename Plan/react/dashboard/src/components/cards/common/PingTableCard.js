import React from 'react';
import {Card} from "react-bootstrap";
import CardHeader from "../CardHeader";
import {faWifi} from "@fortawesome/free-solid-svg-icons";
import PingTable from "../../table/PingTable";

const PingTableCard = ({data}) => {
    return (
        <Card id={'ping-per-country'}>
            <CardHeader icon={faWifi} color="green" label={'html.label.connectionInfo'}/>
            <PingTable countries={data?.table || []}/>
        </Card>
    )
};

export default PingTableCard