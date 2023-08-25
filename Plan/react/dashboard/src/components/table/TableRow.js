import {FontAwesomeIcon as Fa} from "@fortawesome/react-fontawesome";
import React from "react";

export const TableRow = ({icon, text, color, values, bold}) => {
    if (!values || values.filter(value => value !== undefined).length < values.length) return <></>;

    const label = (<><Fa icon={icon} className={'col-' + color}/> {text}</>);
    return (
        <tr>
            <td>{bold ? <b>{label}</b> : label}</td>
            {values.map((value, j) => <td key={j}>{value}</td>)}
        </tr>
    )
}