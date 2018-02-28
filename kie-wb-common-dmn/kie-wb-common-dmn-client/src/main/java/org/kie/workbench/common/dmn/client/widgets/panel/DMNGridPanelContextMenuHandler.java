/*
 * Copyright 2018 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kie.workbench.common.dmn.client.widgets.panel;

import java.util.Optional;
import java.util.stream.Stream;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.ait.lienzo.client.core.types.Point2D;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.ContextMenuEvent;
import com.google.gwt.event.dom.client.ContextMenuHandler;
import org.kie.workbench.common.dmn.api.qualifiers.DMNEditor;
import org.kie.workbench.common.dmn.client.editors.expressions.types.context.ExpressionCellValue;
import org.kie.workbench.common.dmn.client.widgets.grid.controls.HasCellEditorControls;
import org.kie.workbench.common.dmn.client.widgets.grid.controls.container.CellEditorControls;
import org.kie.workbench.common.dmn.client.widgets.layer.DMNGridLayer;
import org.uberfire.ext.wires.core.grids.client.model.GridCell;
import org.uberfire.ext.wires.core.grids.client.model.GridCellValue;
import org.uberfire.ext.wires.core.grids.client.model.GridColumn;
import org.uberfire.ext.wires.core.grids.client.model.GridData;
import org.uberfire.ext.wires.core.grids.client.model.GridData.SelectedCell;
import org.uberfire.ext.wires.core.grids.client.util.CoordinateUtilities;
import org.uberfire.ext.wires.core.grids.client.widget.grid.GridWidget;
import org.uberfire.ext.wires.core.grids.client.widget.grid.selections.CellSelectionStrategy;
import org.uberfire.ext.wires.core.grids.client.widget.grid.selections.impl.RangeSelectionStrategy;

@DMNEditor
@ApplicationScoped
public class DMNGridPanelContextMenuHandler implements ContextMenuHandler {

    private DMNGridLayer gridLayer;
    private CellEditorControls cellEditorControls;

    public DMNGridPanelContextMenuHandler() {
        //CDI proxy
    }

    @Inject
    public DMNGridPanelContextMenuHandler(final @DMNEditor DMNGridLayer gridLayer,
                                          final CellEditorControls cellEditorControls) {
        this.gridLayer = gridLayer;
        this.cellEditorControls = cellEditorControls;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onContextMenu(final ContextMenuEvent event) {
        event.preventDefault();
        event.stopPropagation();
        final boolean isShiftKeyDown = event.getNativeEvent().getShiftKey();
        final boolean isControlKeyDown = event.getNativeEvent().getCtrlKey();
        final int canvasX = getRelativeX(event);
        final int canvasY = getRelativeY(event);

        for (GridWidget gridWidget : gridLayer.getGridWidgets()) {
            final GridData gridModel = gridWidget.getModel();

            final Point2D ap = CoordinateUtilities.convertDOMToGridCoordinate(gridWidget,
                                                                              new Point2D(canvasX,
                                                                                          canvasY));
            final Integer uiRowIndex = CoordinateUtilities.getUiRowIndex(gridWidget,
                                                                         ap.getY());
            final Integer uiColumnIndex = CoordinateUtilities.getUiColumnIndex(gridWidget,
                                                                               ap.getX());
            if (uiRowIndex == null || uiColumnIndex == null) {
                continue;
            }

            final GridCell<?> cell = gridWidget.getModel().getCell(uiRowIndex, uiColumnIndex);
            if (cell == null) {
                continue;
            }
            final GridCellValue<?> value = cell.getValue();
            if (value instanceof ExpressionCellValue) {
                continue;
            }
            if (cell instanceof HasCellEditorControls) {
                final HasCellEditorControls hasControls = (HasCellEditorControls) cell;
                final Optional<HasCellEditorControls.Editor> editor = hasControls.getEditor();
                editor.ifPresent(e -> {
                    e.bind(gridWidget,
                           uiRowIndex,
                           uiColumnIndex);
                    cellEditorControls.show(e,
                                            (int) (ap.getX() + gridWidget.getAbsoluteX()),
                                            (int) (ap.getY() + gridWidget.getAbsoluteY()));
                });
            }

            //If the right-click did not occur in an already selected cell, ensure the cell is selected
            final GridColumn<?> column = gridModel.getColumns().get(uiColumnIndex);
            final int modelColumnIndex = column.getIndex();
            final Stream<SelectedCell> modelColumnSelectedCells = gridModel.getSelectedCells().stream().filter(sc -> sc.getColumnIndex() == modelColumnIndex);
            final boolean isContextMenuCellSelectedCell = modelColumnSelectedCells.map(SelectedCell::getRowIndex).anyMatch(uiRowIndex::equals);
            if (!isContextMenuCellSelectedCell) {
                selectCell(uiRowIndex,
                           uiColumnIndex,
                           gridWidget,
                           isShiftKeyDown,
                           isControlKeyDown);
            }
        }
    }

    private int getRelativeX(final ContextMenuEvent event) {
        final NativeEvent e = event.getNativeEvent();
        final Element target = event.getRelativeElement();
        return e.getClientX() - target.getAbsoluteLeft() + target.getScrollLeft() + target.getOwnerDocument().getScrollLeft();
    }

    private int getRelativeY(final ContextMenuEvent event) {
        final NativeEvent e = event.getNativeEvent();
        final Element target = event.getRelativeElement();
        return e.getClientY() - target.getAbsoluteTop() + target.getScrollTop() + target.getOwnerDocument().getScrollTop();
    }

    private void selectCell(final int uiRowIndex,
                            final int uiColumnIndex,
                            final GridWidget gridWidget,
                            final boolean isShiftKeyDown,
                            final boolean isControlKeyDown) {
        // Lookup CellSelectionManager for cell
        final GridData gridModel = gridWidget.getModel();

        CellSelectionStrategy selectionStrategy;
        final GridCell<?> cell = gridModel.getCell(uiRowIndex,
                                                   uiColumnIndex);
        if (cell == null) {
            selectionStrategy = RangeSelectionStrategy.INSTANCE;
        } else {
            selectionStrategy = cell.getSelectionStrategy();
        }
        if (selectionStrategy == null) {
            return;
        }

        // Handle selection
        if (selectionStrategy.handleSelection(gridModel,
                                              uiRowIndex,
                                              uiColumnIndex,
                                              isShiftKeyDown,
                                              isControlKeyDown)) {
            gridWidget.getLayer().batch();
        }
    }
}