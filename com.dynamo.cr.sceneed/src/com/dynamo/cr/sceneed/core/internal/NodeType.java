package com.dynamo.cr.sceneed.core.internal;

import java.util.ArrayList;
import java.util.List;

import com.dynamo.cr.editor.core.IResourceType;
import com.dynamo.cr.sceneed.core.INodeLoader;
import com.dynamo.cr.sceneed.core.INodeRenderer;
import com.dynamo.cr.sceneed.core.INodeType;
import com.dynamo.cr.sceneed.core.ISceneView;
import com.dynamo.cr.sceneed.core.ISceneView.INodePresenter;
import com.dynamo.cr.sceneed.core.Node;
import com.google.protobuf.Message;

public class NodeType implements INodeType {

    private final String extension;
    private final INodeLoader<? extends Node, ? extends Message> loader;
    private final ISceneView.INodePresenter<Node> presenter;
    private final INodeRenderer<Node> renderer;
    private final IResourceType resourceType;
    private final Class<?> nodeClass;
    private final List<INodeType> referenceNodeTypes;
    private final String displayGroup;

    public NodeType(String extension, INodeLoader<? extends Node, ? extends Message> loader, ISceneView.INodePresenter<Node> presenter, INodeRenderer<Node> renderer, IResourceType resourceType, Class<?> nodeClass, String displayGroup) {
        this.extension = extension;
        this.loader = loader;
        this.presenter = presenter;
        this.renderer = renderer;
        this.resourceType = resourceType;
        this.nodeClass = nodeClass;
        this.referenceNodeTypes = new ArrayList<INodeType>();
        this.displayGroup = displayGroup;
    }

    @Override
    public String getExtension() {
        return this.extension;
    }

    @Override
    public INodeLoader<? extends Node, ? extends Message> getLoader() {
        return this.loader;
    }

    @Override
    public INodePresenter<?> getPresenter() {
        return this.presenter;
    }

    @Override
    public INodeRenderer<Node> getRenderer() {
        return this.renderer;
    }

    @Override
    public IResourceType getResourceType() {
        return this.resourceType;
    }

    @Override
    public Class<?> getNodeClass() {
        return this.nodeClass;
    }

    @Override
    public List<INodeType> getReferenceNodeTypes() {
        return this.referenceNodeTypes;
    }

    @Override
    public String getDisplayGroup() {
        return this.displayGroup;
    }

    public void addReferenceNodeType(INodeType referenceNodeType) {
        this.referenceNodeTypes.add(referenceNodeType);
    }

}
