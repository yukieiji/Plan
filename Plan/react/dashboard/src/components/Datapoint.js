import {FontAwesomeIcon as Fa} from "@fortawesome/react-fontawesome";
import React from "react";
import End from "./layout/End";

const Datapoint = ({icon, color, name, value, valueLabel, bold, boldTitle, title, trend}) => {
    if (value === undefined && valueLabel === undefined) return <></>;

    const displayedValue = bold ? <b>{value}</b> : value;
    const extraLabel = typeof valueLabel === 'string' ? ` (${valueLabel})` : '';
    const colorClass = color && color.startsWith("col-") ? color : "col-" + color;
    return (
        <p title={title ? title : name + " is " + value}>
            {icon && <Fa icon={icon} className={colorClass}/>} {boldTitle ? <b>{name}</b> : name}
            {value !== undefined ? <End>{displayedValue} {extraLabel}{trend}</End> : ''}
        </p>
    );
}

export default Datapoint