package com.dynamo.cr.tileeditor.core;

import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.operations.IOperationHistory;
import org.eclipse.core.commands.operations.IUndoContext;
import org.eclipse.core.commands.operations.IUndoableOperation;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;

import com.dynamo.cr.properties.Entity;
import com.dynamo.cr.properties.IPropertyModel;
import com.dynamo.cr.properties.Property;
import com.dynamo.cr.properties.PropertyIntrospector;
import com.dynamo.cr.properties.PropertyIntrospectorModel;
import com.dynamo.cr.tileeditor.Activator;
import com.dynamo.cr.tileeditor.core.Layer.Cell;
import com.dynamo.tile.proto.Tile;
import com.dynamo.tile.proto.Tile.TileCell;
import com.dynamo.tile.proto.Tile.TileGrid;
import com.dynamo.tile.proto.Tile.TileLayer;
import com.google.protobuf.TextFormat;

@Entity(commandFactory = GridUndoableCommandFactory.class)
public class GridModel extends Model implements ITileWorld, IAdaptable {

    public static PropertyIntrospector<Layer, GridModel> layerIntrospector = new PropertyIntrospector<Layer, GridModel>(Layer.class);

    @Property(isResource = true)
    @Resource
    private String tileSet;

    private final IOperationHistory history;
    private final IUndoContext undoContext;
    private final ILogger logger;
    private final IContainer contentRoot;
    private TileSetModel tileSetModel;
    private Layer selectedLayer;

    private List<Layer> layers = new ArrayList<Layer>();

    @Inject
    public GridModel(IContainer contentRoot, IOperationHistory history, IUndoContext undoContext, ILogger logger) {
        this.contentRoot = contentRoot;
        this.history = history;
        this.undoContext = undoContext;
        this.logger = logger;
    }

    @Override
    public IContainer getContentRoot() {
        return contentRoot;
    }

    public TileSetModel getTileSetModel() {
        return this.tileSetModel;
    }

    public boolean isValid() {
        PropertyIntrospectorModel<GridModel, GridModel> propertyModel = new PropertyIntrospectorModel<GridModel, GridModel>(this, this, introspector);
        return propertyModel.isValid();
    }

    public String getTileSet() {
        return this.tileSet;
    }

    public void setTileSet(String tileSet) {
        if ((this.tileSet == null && tileSet != null) || (this.tileSet != null && !this.tileSet.equals(tileSet))) {
            String oldTileSet = this.tileSet;
            this.tileSet = tileSet;
            if (this.tileSet != null && !this.tileSet.equals("")) {
                if (this.tileSetModel == null) {
                    this.tileSetModel = new TileSetModel(this.contentRoot, null, null, this.logger);
                }
                IFile file = this.contentRoot.getFile(new Path(this.tileSet));
                try {
                    this.tileSetModel.load(file.getContents());
                } catch (Exception e) {
                    // TODO: Report error
                    assert false;
                }
            } else {
                this.tileSetModel = null;
            }
            firePropertyChangeEvent(new PropertyChangeEvent(this, "tileSet", oldTileSet, tileSet));
        }
    }

    protected IStatus validateTileSet() {
        if (this.tileSetModel != null) {
            @SuppressWarnings("unchecked")
            IPropertyModel<TileSetModel, TileSetModel> propertyModel = (IPropertyModel<TileSetModel, TileSetModel>) this.tileSetModel.getAdapter(IPropertyModel.class);
            IStatus imageStatus = propertyModel.getPropertyStatus("image");
            if (imageStatus != null && !imageStatus.isOK()) {
                return new Status(IStatus.ERROR, Activator.PLUGIN_ID, Messages.GRID_INVALID_TILESET);
            }
        }
        return Status.OK_STATUS;
    }

    public List<Layer> getLayers() {
        return Collections.unmodifiableList(this.layers);
    }

    public void setLayers(List<Layer> layers) {
        boolean fire = this.layers.equals(layers);

        List<Layer> oldLayers = this.layers;
        this.layers = new ArrayList<Layer>(layers);

        Set<String> idSet = new HashSet<String>();
        boolean duplication = false;
        for (Layer layer : this.layers) {
            layer.setGridModel(this);
            if (idSet.contains(layer.getId())) {
                duplication = true;
            } else {
                idSet.add(layer.getId());
            }
        }

        if (duplication) {
            // TODO: Do something useful?
        }

        if (fire)
            firePropertyChangeEvent(new PropertyChangeEvent(this, "layers", oldLayers, layers));
    }

    public Layer createLayer() {
        Layer layer = new Layer();
        String pattern = "layer{0}";
        List<String> layerIds = new ArrayList<String>(this.layers.size());
        for (Layer l : this.layers) {
            layerIds.add(l.getId());
        }
        int i = 1;
        String layerId = MessageFormat.format(pattern, i++);
        while (layerIds.contains(layerId)) {
            layerId = MessageFormat.format(pattern, i++);
        }
        layer.setId(layerId);
        return layer;
    }

    public void addLayer(Layer layer) {
        if (!this.layers.contains(layer)) {
            layer.setGridModel(this);
            List<Layer> oldLayers = this.layers;
            this.layers = new ArrayList<Layer>(oldLayers);
            this.layers.add(layer);
            Collections.sort(this.layers);
            firePropertyChangeEvent(new PropertyChangeEvent(this, "layers", oldLayers, this.layers));
        }
    }

    public void removeLayer(Layer layer) {
        if (this.layers.contains(layer)) {
            List<Layer> oldLayers = this.layers;
            this.layers = new ArrayList<Layer>(oldLayers);
            this.layers.remove(layer);
            firePropertyChangeEvent(new PropertyChangeEvent(this, "layers", oldLayers, this.layers));
        }
    }

    public void sortLayers() {
        List<Layer> oldLayers = new ArrayList<Layer>(this.layers);
        Collections.sort(this.layers);
        if (!oldLayers.equals(this.layers)) {
            firePropertyChangeEvent(new PropertyChangeEvent(this, "layers", oldLayers, this.layers));
        }
    }

    public Layer getSelectedLayer() {
        return this.selectedLayer;
    }

    public void setSelectedLayer(Layer selectedLayer) {
        if (this.selectedLayer != selectedLayer) {
            Layer prevSelectedLayer = this.selectedLayer;
            this.selectedLayer = selectedLayer;
            firePropertyChangeEvent(new PropertyChangeEvent(this, "selectedLayer", prevSelectedLayer, this.selectedLayer));
        }
    }

    public Map<Long, Cell> getCells() {
        return this.selectedLayer.getCells();
    }

    public void setCells(Map<Long, Cell> cells) {
        Map<Long, Cell> oldCells = this.selectedLayer.getCells();
        if (!oldCells.equals(cells)) {
            this.selectedLayer.setCells(cells);
            firePropertyChangeEvent(new PropertyChangeEvent(this.selectedLayer, "cells", oldCells, cells));
        }
    }

    public Cell getCell(long cellIndex) {
        return this.selectedLayer.getCell(cellIndex);
    }

    public void setCell(long cellIndex, Cell cell) {
        this.selectedLayer.setCell(cellIndex, cell);
    }

    public void load(InputStream is) {
        TileGrid.Builder tileGridBuilder = TileGrid.newBuilder();
        try {
            TextFormat.merge(new InputStreamReader(is), tileGridBuilder);
            TileGrid tileGrid = tileGridBuilder.build();
            setTileSet(tileGrid.getTileSet());
            List<Layer> layers = new ArrayList<Layer>(tileGrid.getLayersCount());
            for (Tile.TileLayer layerDDF : tileGrid.getLayersList()) {
                Layer layer = new Layer();
                layer.setGridModel(this);
                layer.setId(layerDDF.getId());
                layer.setZ(layerDDF.getZ());
                layer.setVisible(layerDDF.getIsVisible() != 0);

                for (TileCell cellDDF : layerDDF.getCellList()) {
                    long cellIndex = Layer.toCellIndex(cellDDF.getX(), cellDDF.getY());
                    Cell cell = new Cell(cellDDF.getTile(), cellDDF.getHFlip() != 0 ? true : false, cellDDF.getVFlip() != 0 ? true : false);
                    layer.setCell(cellIndex, cell);
                }
                layers.add(layer);
            }
            setLayers(layers);
            if (layers.size() > 0) {
                setSelectedLayer(layers.get(0));
            } else {
                setSelectedLayer(null);
            }
        } catch (IOException e) {
            logger.logException(e);
        }
    }

    public void save(OutputStream os, IProgressMonitor monitor) throws IOException {
        TileGrid.Builder tileGridBuilder = TileGrid.newBuilder()
                .setTileSet(this.tileSet);
        for (Layer layer : this.layers) {
            TileLayer.Builder layerBuilder = TileLayer.newBuilder()
                    .setId(layer.getId())
                    .setZ(layer.getZ())
                    .setIsVisible(layer.isVisible() ? 1 : 0);
            for (Map.Entry<Long, Cell> entry : layer.getCells().entrySet()) {
                long key = entry.getKey();
                int x = Layer.toCellX(key);
                int y = Layer.toCellY(key);
                Cell cell = entry.getValue();
                TileCell.Builder cellBuilder = TileCell.newBuilder()
                        .setX(x)
                        .setY(y)
                        .setTile(cell.getTile())
                        .setHFlip(cell.isHFlip() ? 1 : 0)
                        .setVFlip(cell.isVFlip() ? 1 : 0);
                layerBuilder.addCell(cellBuilder);
            }
            tileGridBuilder.addLayers(layerBuilder);
        }
        TileGrid tileGrid = tileGridBuilder.build();
        try {
            OutputStreamWriter writer = new OutputStreamWriter(os);
            try {
                TextFormat.print(tileGrid, writer);
                writer.flush();
            } finally {
                writer.close();
            }
        } catch (IOException e) {
            throw e;
        }
    }

    private static PropertyIntrospector<GridModel, GridModel> introspector = new PropertyIntrospector<GridModel, GridModel>(GridModel.class, Messages.class);

    @Override
    public Object getAdapter(@SuppressWarnings("rawtypes") Class adapter) {
        if (adapter == IPropertyModel.class) {
            return new PropertyIntrospectorModel<GridModel, GridModel>(this, this, introspector);
        }
        return null;
    }

    public void executeOperation(IUndoableOperation operation) {
        operation.addContext(this.undoContext);
        IStatus status = null;
        try {
            status = this.history.execute(operation, null, null);
        } catch (final ExecutionException e) {
            this.logger.logException(e);
        }

        if (status != Status.OK_STATUS) {
            this.logger.logException(status.getException());
        }
    }

    public boolean handleResourceChanged(IResourceChangeEvent event) throws CoreException, IOException {
        if (this.tileSetModel != null && this.tileSetModel.handleResourceChanged(event)) {
            return true;
        } else {
            if (this.tileSet != null && this.tileSet.length() > 0) {
                final IFile file = this.contentRoot.getFile(new Path(this.tileSet));
                final boolean[] reload = new boolean[] {false};

                try {
                    event.getDelta().accept(new IResourceDeltaVisitor() {

                        @Override
                        public boolean visit(IResourceDelta delta) throws CoreException {
                            IResource resource = delta.getResource();

                            if (file.equals(resource)) {
                                reload[0] = true;
                                return false;
                            }
                            return true;
                        }
                    });
                } catch (CoreException e) {
                    this.logger.logException(e);
                }

                if (reload[0]) {
                    this.tileSetModel.load(file.getContents());
                }
                return reload[0];
            } else {
                return false;
            }
        }
    }

}
