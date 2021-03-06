/*
 * Copyright (c) 2002-2019 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal.compiler.v3_5.planner

import org.neo4j.cypher.internal.compiler.v3_5.{MissingLabelNotification, MissingPropertyNameNotification, MissingRelTypeNotification}
import org.neo4j.cypher.internal.compiler.v3_5.phases.LogicalPlanState
import org.neo4j.cypher.internal.v3_5.expressions.{LabelName, Property, PropertyKeyName, RelTypeName}
import org.neo4j.cypher.internal.v3_5.frontend.phases.CompilationPhaseTracer.CompilationPhase.LOGICAL_PLANNING
import org.neo4j.cypher.internal.v3_5.frontend.phases.{BaseContext, VisitorPhase}
import org.neo4j.cypher.internal.v3_5.util.InternalNotification

import org.neo4j.values.storable.TemporalValue.TemporalFields
import org.neo4j.values.storable.{DurationFields, PointFields}

import scala.collection.JavaConverters._

object CheckForUnresolvedTokens extends VisitorPhase[BaseContext, LogicalPlanState] {

  private val specialPropertyKey: Set[String] =
    (TemporalFields.allFields().asScala ++
      DurationFields.values().map(_.propertyKey) ++
      PointFields.values().map(_.propertyKey)).map(_.toLowerCase).toSet

  override def visit(value: LogicalPlanState, context: BaseContext): Unit = {
    val table = value.semanticTable()
    def isEmptyLabel(label: String) = !table.resolvedLabelNames.contains(label)
    def isEmptyRelType(relType: String) = !table.resolvedRelTypeNames.contains(relType)
    def isEmptyPropertyName(name: String) = !table.resolvedPropertyKeyNames.contains(name)

    val notifications = value.statement().treeFold(Seq.empty[InternalNotification]) {
      case label@LabelName(name) if isEmptyLabel(name) => acc =>
        (acc :+ MissingLabelNotification(label.position, name), Some(identity))

      case rel@RelTypeName(name) if isEmptyRelType(name) => acc =>
        (acc :+ MissingRelTypeNotification(rel.position, name), Some(identity))

      case Property(_, prop@PropertyKeyName(name)) if !specialPropertyKey(name.toLowerCase) && isEmptyPropertyName(name) => acc =>
        (acc :+ MissingPropertyNameNotification(prop.position, name), Some(identity))
    }

    notifications foreach context.notificationLogger.log
  }

  override def phase = LOGICAL_PLANNING

  override def description = "find labels, relationships types and property keys that do not exist in the db and issue warnings"
}
