import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useHistory } from 'react-router';

import { Alert, Button, Drawer } from 'antd';
import { InfoCircleOutlined } from '@ant-design/icons';
import styled from 'styled-components';

import { Message } from '../shared/Message';
import { useEntityRegistry } from '../useEntityRegistry';
import CompactContext from '../shared/CompactContext';
import { Direction, EntityAndType, EntitySelectParams, FetchedEntities, LineageExpandParams } from './types';
import getChildren from './utils/getChildren';
import LineageViz from './LineageViz';
import extendAsyncEntities from './utils/extendAsyncEntities';
import useLazyGetEntityQuery from './utils/useLazyGetEntityQuery';
import useGetEntityQuery from './utils/useGetEntityQuery';
import { EntityType } from '../../types.generated';
import { capitalizeFirstLetter } from '../shared/capitalizeFirstLetter';
import { ANTD_GRAY } from '../entity/shared/constants';

const LoadingMessage = styled(Message)`
    margin-top: 10%;
`;
const FooterButtonGroup = styled.div`
    display: flex;
    justify-content: space-between;
    margin: 12px 0;
`;

const EntityDrawer = styled(Drawer)`
    top: 106px;
    z-index: 1;
    height: calc(100vh - 106px);
    .ant-drawer-content-wrapper {
        border-right: 1px solid ${ANTD_GRAY[4.5]};
        box-shadow: none !important;
    }
`;

function usePrevious(value) {
    const ref = useRef();
    useEffect(() => {
        ref.current = value;
    });
    return ref.current;
}

type Props = {
    urn: string;
    type: EntityType;
};

export default function LineageExplorer({ urn, type }: Props) {
    const previousUrn = usePrevious(urn);
    const history = useHistory();

    const entityRegistry = useEntityRegistry();

    const { loading, error, data } = useGetEntityQuery(urn, type);
    const { getAsyncEntity, asyncData } = useLazyGetEntityQuery();

    const [isDrawerVisible, setIsDrawVisible] = useState(false);
    const [selectedEntity, setSelectedEntity] = useState<EntitySelectParams | undefined>(undefined);
    const [asyncEntities, setAsyncEntities] = useState<FetchedEntities>({});

    const maybeAddAsyncLoadedEntity = useCallback(
        (entityAndType: EntityAndType) => {
            if (entityAndType?.entity.urn && !asyncEntities[entityAndType?.entity.urn]?.fullyFetched) {
                // record that we have added this entity
                let newAsyncEntities = extendAsyncEntities(asyncEntities, entityRegistry, entityAndType, true);

                // add the partially fetched downstream & upstream datasets
                getChildren(entityAndType, Direction.Downstream).forEach((downstream) => {
                    newAsyncEntities = extendAsyncEntities(newAsyncEntities, entityRegistry, downstream, false);
                });
                getChildren(entityAndType, Direction.Upstream).forEach((upstream) => {
                    newAsyncEntities = extendAsyncEntities(newAsyncEntities, entityRegistry, upstream, false);
                });
                setAsyncEntities(newAsyncEntities);
            }
        },
        [asyncEntities, setAsyncEntities, entityRegistry],
    );

    const handleClose = () => {
        setIsDrawVisible(false);
        setSelectedEntity(undefined);
    };

    useEffect(() => {
        if (type && data) {
            maybeAddAsyncLoadedEntity(data);
        }
        if (asyncData) {
            maybeAddAsyncLoadedEntity(asyncData);
        }
    }, [data, asyncData, asyncEntities, setAsyncEntities, maybeAddAsyncLoadedEntity, urn, previousUrn, type]);

    if (error || (!loading && !error && !data)) {
        return <Alert type="error" message={error?.message || 'Entity failed to load'} />;
    }

    return (
        <>
            {loading && <LoadingMessage type="loading" content="Loading..." />}
            {!!data && (
                <div>
                    <LineageViz
                        selectedEntity={selectedEntity}
                        fetchedEntities={asyncEntities}
                        entityAndType={data}
                        onEntityClick={(params: EntitySelectParams) => {
                            setIsDrawVisible(true);
                            setSelectedEntity(params);
                        }}
                        onEntityCenter={(params: EntitySelectParams) => {
                            history.push(
                                `${entityRegistry.getEntityUrl(params.type, params.urn)}/?is_lineage_mode=true`,
                            );
                        }}
                        onLineageExpand={(params: LineageExpandParams) => {
                            getAsyncEntity(params.urn, params.type);
                        }}
                    />
                </div>
            )}
            <EntityDrawer
                placement="left"
                closable={false}
                onClose={handleClose}
                visible={isDrawerVisible}
                width={490}
                mask={false}
                footer={
                    selectedEntity && (
                        <FooterButtonGroup>
                            <Button onClick={handleClose} type="text">
                                Close
                            </Button>
                            <Button href={entityRegistry.getEntityUrl(selectedEntity.type, selectedEntity.urn)}>
                                <InfoCircleOutlined /> {capitalizeFirstLetter(selectedEntity.type)} Details
                            </Button>
                        </FooterButtonGroup>
                    )
                }
            >
                <CompactContext.Provider value>
                    {selectedEntity && entityRegistry.renderProfile(selectedEntity.type, selectedEntity.urn)}
                </CompactContext.Provider>
            </EntityDrawer>
        </>
    );
}
