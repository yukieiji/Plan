import React, {useEffect, useState} from "react";
import {Card, Col} from "react-bootstrap";
import ExtensionIcon from "./ExtensionIcon";
import Datapoint from "../Datapoint";
import Masonry from 'masonry-layout'
import ExtensionTable from "./ExtensionTable";
import {FontAwesomeIcon as Fa} from "@fortawesome/react-fontawesome";
import End from "../layout/End";
import {MinecraftChat} from "react-mcjsonchat";

export const ExtensionCardWrapper = ({extension, children}) => {
    const [windowWidth, setWindowWidth] = useState(window.innerWidth);

    const onResize = () => {
        setWindowWidth(window.innerWidth);
    }

    useEffect(() => {
        window.addEventListener("resize", onResize);
        return () => {
            window.removeEventListener("resize", onResize);
        }
    }, [])

    const wide = extension.wide;

    const baseWidth = windowWidth < 1000 ? 6 : 4;
    const width = (wide ? 2 : 1) * baseWidth;

    return <Col lg={width} md={width} className="extension-wrapper">{children}</Col>
}

const ExtensionTab = ({tab}) => {
    return (<>
        {tab.tabInformation.elementOrder.map((type, i) => {
            switch (type) {
                case "VALUES":
                    return <ExtensionValues key={i} tab={tab}/>
                case "TABLE":
                    return <ExtensionTables key={i} tab={tab}/>
                default:
                    return ''
            }
        })}
    </>);
}

const ExtensionValue = ({data}) => {
    const color = data.description.icon.colorClass;
    const colorClass = color && color.startsWith("col-") ? color : "col-" + color;
    const icon = [data.description.icon.familyClass, data.description.icon.iconName];
    const name = data.description.text;
    const title = data.description.description;
    if (data.type === 'HTML') {
        return (
            <p title={title}>
                {icon && <Fa icon={icon} className={colorClass}/>} {name}
                {<End><span dangerouslySetInnerHTML={data.value}></span></End>}
            </p>
        );
    } else if (data.type === 'COMPONENT') {
        return (<p title={title}>
            {icon && <Fa icon={icon} className={colorClass}/>} {name}
            {<End><MinecraftChat component={JSON.parse(data.value)}/></End>}
        </p>)
    } else {
        return (<Datapoint name={name}
                           title={title}
                           icon={icon}
                           color={color}
                           value={data.value}
        />);
    }
}

const ExtensionValues = ({tab}) => {
    return (
        <>
            {Boolean(tab.values.length) && <Card.Body>
                {tab.values.map((data, i) => {
                        return (<ExtensionValue key={i} data={data}/>);
                    }
                )}
            </Card.Body>}
        </>
    )
}

const ExtensionTables = ({tab}) => {
    return (<>
        {tab.tableData.map((table, i) => (
            <ExtensionTable key={i} table={table}/>
        ))}
    </>);
}

const ExtensionCard = ({extension}) => {
    const [openTabIndex, setOpenTabIndex] = useState(0);

    const toggleTabIndex = index => {
        setOpenTabIndex(index);

        requestAnimationFrame(() => {
            const masonryRow = document.getElementById('extension-masonry-row');
            const masonry = Masonry.data(masonryRow);
            if (masonry) {
                masonry.layout();
            }
        });
    }

    return <Card>
        <Card.Header>
            <h6>
                <ExtensionIcon
                    icon={extension.extensionInformation.icon}/> {extension.extensionInformation.pluginName}
            </h6>
        </Card.Header>
        <ul className="nav nav-tabs tab-nav-right" role="tablist">
            {extension.onlyGenericTab ? '' :
                extension.tabs.map((tab, i) => <li key={JSON.stringify(tab)} role="presentation"
                                                   className="nav-item col-black">
                    <button className={"nav-link col-black"
                        + (openTabIndex === i ? ' active' : '')} onClick={() => toggleTabIndex(i)}>
                        <ExtensionIcon icon={tab.tabInformation.icon}/> {tab.tabInformation.tabName}
                    </button>
                </li>)
            }
        </ul>
        {extension.tabs.map((tab, i) => openTabIndex === i ? <ExtensionTab tab={tab} key={i}/> : '')}
    </Card>
}

export default ExtensionCard