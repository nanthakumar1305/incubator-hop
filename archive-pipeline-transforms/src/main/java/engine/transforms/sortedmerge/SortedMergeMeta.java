/*! ******************************************************************************
 *
 * Pentaho Data Integration
 *
 * Copyright (C) 2002-2017 by Hitachi Vantara : http://www.pentaho.com
 *
 *******************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package org.apache.hop.pipeline.transforms.sortedmerge;

import org.apache.hop.core.CheckResult;
import org.apache.hop.core.CheckResultInterface;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.exception.HopXMLException;
import org.apache.hop.core.injection.AfterInjection;
import org.apache.hop.core.injection.Injection;
import org.apache.hop.core.injection.InjectionSupported;
import org.apache.hop.core.row.RowMetaInterface;
import org.apache.hop.core.row.ValueMetaInterface;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.VariableSpace;
import org.apache.hop.core.xml.XMLHandler;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metastore.api.IMetaStore;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.PipelineMeta.PipelineType;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.TransformDataInterface;
import org.apache.hop.pipeline.transform.TransformInterface;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transform.TransformMetaInterface;
import org.w3c.dom.Node;

import java.util.List;

/*
 * Created on 02-jun-2003
 *
 */
@InjectionSupported( localizationPrefix = "SortedMerge.Injection.", groups = { "FIELDS" } )
public class SortedMergeMeta extends BaseTransformMeta implements TransformMetaInterface {
  private static Class<?> PKG = SortedMergeMeta.class; // for i18n purposes, needed by Translator!!

  /**
   * order by which fields?
   */
  @Injection( name = "FIELD_NAME", group = "FIELDS" )
  private String[] fieldName;
  /**
   * false : descending, true=ascending
   */
  @Injection( name = "ASCENDING", group = "FIELDS" )
  private boolean[] ascending;

  public void loadXML( Node transformNode, IMetaStore metaStore ) throws HopXMLException {
    readData( transformNode );
  }

  public void allocate( int nrFields ) {
    fieldName = new String[ nrFields ]; // order by
    ascending = new boolean[ nrFields ];
  }

  public void setDefault() {
    int nrFields = 0;

    allocate( nrFields );

    for ( int i = 0; i < nrFields; i++ ) {
      fieldName[ i ] = "field" + i;
    }
  }

  public Object clone() {
    SortedMergeMeta retval = (SortedMergeMeta) super.clone();

    int nrFields = fieldName.length;

    retval.allocate( nrFields );
    System.arraycopy( fieldName, 0, retval.fieldName, 0, nrFields );
    System.arraycopy( ascending, 0, retval.ascending, 0, nrFields );

    return retval;
  }

  private void readData( Node transformNode ) throws HopXMLException {
    try {
      Node fields = XMLHandler.getSubNode( transformNode, "fields" );
      int nrFields = XMLHandler.countNodes( fields, "field" );

      allocate( nrFields );

      for ( int i = 0; i < nrFields; i++ ) {
        Node fnode = XMLHandler.getSubNodeByNr( fields, "field", i );

        fieldName[ i ] = XMLHandler.getTagValue( fnode, "name" );
        String asc = XMLHandler.getTagValue( fnode, "ascending" );
        if ( asc.equalsIgnoreCase( "Y" ) ) {
          ascending[ i ] = true;
        } else {
          ascending[ i ] = false;
        }
      }
    } catch ( Exception e ) {
      throw new HopXMLException( "Unable to load transform info from XML", e );
    }
  }

  public String getXML() {
    StringBuilder retval = new StringBuilder();

    retval.append( "    <fields>" + Const.CR );
    for ( int i = 0; i < fieldName.length; i++ ) {
      retval.append( "      <field>" + Const.CR );
      retval.append( "        " + XMLHandler.addTagValue( "name", fieldName[ i ] ) );
      retval.append( "        " + XMLHandler.addTagValue( "ascending", ascending[ i ] ) );
      retval.append( "        </field>" + Const.CR );
    }
    retval.append( "      </fields>" + Const.CR );

    return retval.toString();
  }

  public void getFields( RowMetaInterface inputRowMeta, String name, RowMetaInterface[] info, TransformMeta nextTransform,
                         VariableSpace space, IMetaStore metaStore ) throws HopTransformException {
    // Set the sorted properties: ascending/descending
    for ( int i = 0; i < fieldName.length; i++ ) {
      int idx = inputRowMeta.indexOfValue( fieldName[ i ] );
      if ( idx >= 0 ) {
        ValueMetaInterface valueMeta = inputRowMeta.getValueMeta( idx );
        valueMeta.setSortedDescending( !ascending[ i ] );

        // TODO: add case insensivity
      }
    }

  }

  public void check( List<CheckResultInterface> remarks, PipelineMeta pipelineMeta, TransformMeta transformMeta,
                     RowMetaInterface prev, String[] input, String[] output, RowMetaInterface info, VariableSpace space,
                     IMetaStore metaStore ) {
    CheckResult cr;

    if ( prev != null && prev.size() > 0 ) {
      cr =
        new CheckResult( CheckResultInterface.TYPE_RESULT_OK, BaseMessages.getString(
          PKG, "SortedMergeMeta.CheckResult.FieldsReceived", "" + prev.size() ), transformMeta );
      remarks.add( cr );

      String error_message = "";
      boolean error_found = false;

      // Starting from selected fields in ...
      for ( int i = 0; i < fieldName.length; i++ ) {
        int idx = prev.indexOfValue( fieldName[ i ] );
        if ( idx < 0 ) {
          error_message += "\t\t" + fieldName[ i ] + Const.CR;
          error_found = true;
        }
      }
      if ( error_found ) {
        error_message =
          BaseMessages.getString( PKG, "SortedMergeMeta.CheckResult.SortKeysNotFound", error_message );

        cr = new CheckResult( CheckResultInterface.TYPE_RESULT_ERROR, error_message, transformMeta );
        remarks.add( cr );
      } else {
        if ( fieldName.length > 0 ) {
          cr =
            new CheckResult( CheckResultInterface.TYPE_RESULT_OK, BaseMessages.getString(
              PKG, "SortedMergeMeta.CheckResult.AllSortKeysFound" ), transformMeta );
          remarks.add( cr );
        } else {
          cr =
            new CheckResult( CheckResultInterface.TYPE_RESULT_ERROR, BaseMessages.getString(
              PKG, "SortedMergeMeta.CheckResult.NoSortKeysEntered" ), transformMeta );
          remarks.add( cr );
        }
      }
    } else {
      cr =
        new CheckResult( CheckResultInterface.TYPE_RESULT_ERROR, BaseMessages.getString(
          PKG, "SortedMergeMeta.CheckResult.NoFields" ), transformMeta );
      remarks.add( cr );
    }

    // See if we have input streams leading to this transform!
    if ( input.length > 0 ) {
      cr =
        new CheckResult( CheckResultInterface.TYPE_RESULT_OK, BaseMessages.getString(
          PKG, "SortedMergeMeta.CheckResult.ExpectedInputOk" ), transformMeta );
      remarks.add( cr );
    } else {
      cr =
        new CheckResult( CheckResultInterface.TYPE_RESULT_ERROR, BaseMessages.getString(
          PKG, "SortedMergeMeta.CheckResult.ExpectedInputError" ), transformMeta );
      remarks.add( cr );
    }
  }

  public TransformInterface getTransform( TransformMeta transformMeta, TransformDataInterface transformDataInterface, int cnr, PipelineMeta tr,
                                Pipeline pipeline ) {
    return new SortedMerge( transformMeta, transformDataInterface, cnr, tr, pipeline );
  }

  public TransformDataInterface getTransformData() {
    return new SortedMergeData();
  }

  /**
   * @return the ascending
   */
  public boolean[] getAscending() {
    return ascending;
  }

  /**
   * @param ascending the ascending to set
   */
  public void setAscending( boolean[] ascending ) {
    this.ascending = ascending;
  }

  /**
   * @return the fieldName
   */
  public String[] getFieldName() {
    return fieldName;
  }

  /**
   * @param fieldName the fieldName to set
   */
  public void setFieldName( String[] fieldName ) {
    this.fieldName = fieldName;
  }

  public PipelineType[] getSupportedPipelineTypes() {
    return new PipelineType[] { PipelineType.Normal, };
  }

  /**
   * If we use injection we can have different arrays lengths.
   * We need synchronize them for consistency behavior with UI
   */
  @AfterInjection
  public void afterInjectionSynchronization() {
    int nrFields = ( fieldName == null ) ? -1 : fieldName.length;
    if ( nrFields <= 0 ) {
      return;
    }
    boolean[][] rtn = Utils.normalizeArrays( nrFields, ascending );
    ascending = rtn[ 0 ];
  }

}