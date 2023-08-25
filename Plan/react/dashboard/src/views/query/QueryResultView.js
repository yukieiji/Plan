import React, {useCallback, useEffect} from 'react';
import LoadIn from "../../components/animation/LoadIn";
import {Col, Row} from "react-bootstrap";
import QueryPath from "../../components/alert/QueryPath";
import {useQueryResultContext} from "../../hooks/queryResultContext";
import {useNavigate} from "react-router-dom";
import PlayerListCard from "../../components/cards/common/PlayerListCard";
import {fetchExistingResults} from "../../service/queryService";
import {PlayerbaseDevelopmentCardWithData} from "../../components/cards/server/graphs/PlayerbaseDevelopmentCard";
import {CurrentPlayerbaseCardWithData} from "../../components/cards/server/graphs/CurrentPlayerbaseCard";
import {useTranslation} from "react-i18next";
import GeolocationsCard from "../../components/cards/common/GeolocationsCard";
import SessionsWithinViewCard from "../../components/cards/query/SessionsWithinViewCard";
import {useNavigation} from "../../hooks/navigationHook";

const QueryResultView = () => {
    const {t} = useTranslation();
    const navigate = useNavigate();
    const {result, setResult} = useQueryResultContext();
    const {setCurrentTab} = useNavigation()

    const getResult = useCallback(async () => {
        const urlParams = new URLSearchParams(window.location.search);
        const timestamp = urlParams.get('timestamp');
        if (!timestamp) return {};

        const {data: result} = await fetchExistingResults(timestamp);
        if (result) {
            return result;
        } else {
            return {};
        }
    }, [])

    useEffect(() => {
        setCurrentTab('html.query.results.title')
        if (!result.data) {
            getResult().then(data => {
                if (data.data) {
                    setResult(data);
                } else {
                    navigate('../new');
                }
            });
        }
    }, [result, navigate, getResult, setResult, setCurrentTab])

    if (!result.data) {
        return <></>
    }

    const getViewTitle = () => {
        return 'View: ' + result.view.afterDate + " - " + result.view.beforeDate + ', ' +
            (result.view.servers.len ? 'using data of servers: ' + result.view.servers.map(server => server.name).join(', ') : "using data of all servers")
    }

    return (
        <LoadIn>
            <section className={"query-results-view"}>
                <Row>
                    <Col md={12}>
                        <QueryPath/>
                        <PlayerListCard
                            data={result.data.players}
                            title={getViewTitle()}
                        />
                    </Col>
                </Row>
                <Row>
                    <Col lg={8}>
                        <PlayerbaseDevelopmentCardWithData data={result.data.activity}
                                                           title={'html.query.title.activity'}/>
                    </Col>
                    <Col lg={4}>
                        <CurrentPlayerbaseCardWithData data={result.data.activity}
                                                       title={t('html.query.title.activityOnDate')
                                                           .replace('<span id="activity-date"></span>', result.view.beforeDate)}/>
                    </Col>
                </Row>
                <Row>
                    <Col lg={3}>
                        <SessionsWithinViewCard data={result.data.sessions}/>
                    </Col>
                    <Col lg={9}>
                        <GeolocationsCard data={result.data.geolocation}/>
                    </Col>
                </Row>
            </section>
        </LoadIn>
    )
};

export default QueryResultView