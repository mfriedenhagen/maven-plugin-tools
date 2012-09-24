package org.apache.maven.tools.plugin.generator;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.plugin.descriptor.DuplicateMojoDescriptorException;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.Parameter;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.descriptor.Requirement;
import org.apache.maven.project.MavenProject;
import org.apache.maven.tools.plugin.ExtendedMojoDescriptor;
import org.apache.maven.tools.plugin.PluginToolsRequest;
import org.apache.maven.tools.plugin.util.PluginUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.PrettyPrintXMLWriter;
import org.codehaus.plexus.util.xml.XMLWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generate a <a href="/ref/current/maven-plugin-api/plugin.html">Maven Plugin Descriptor XML file</a> and
 * corresponding <code>plugin-help.xml</code> help content for {@link PluginHelpGenerator}.
 *
 * @version $Id$
 * @todo add example usage tag that can be shown in the doco
 * @todo need to add validation directives so that systems embedding maven2 can
 * get validation directives to help users in IDEs.
 */
public class PluginDescriptorGenerator
    implements Generator
{

    /**
     * {@inheritDoc}
     */
    public void execute( File destinationDirectory, PluginToolsRequest request )
        throws GeneratorException
    {
        // eventually rewrite help mojo class to match actual package name
        PluginHelpGenerator.rewriteHelpMojo( request );

        try
        {
            // write complete plugin.xml descriptor
            File f = new File( destinationDirectory, "plugin.xml" );
            writeDescriptor( f, request, false );

            // write plugin-help.xml help-descriptor
            MavenProject mavenProject = request.getProject();

            f =
                new File( mavenProject.getBuild().getOutputDirectory(),
                          PluginHelpGenerator.getPluginHelpPath( mavenProject ) );

            writeDescriptor( f, request, true );
        }
        catch ( IOException e )
        {
            throw new GeneratorException( e.getMessage(), e );
        }
        catch ( DuplicateMojoDescriptorException e )
        {
            throw new GeneratorException( e.getMessage(), e );
        }
    }

    private String getVersion()
    {
        Package p = this.getClass().getPackage();
        String version = ( p == null ) ? null : p.getSpecificationVersion();
        return ( version == null ) ? "SNAPSHOT" : version;
    }

    public void writeDescriptor( File destinationFile, PluginToolsRequest request, boolean helpDescriptor )
        throws IOException, DuplicateMojoDescriptorException
    {
        PluginDescriptor pluginDescriptor = request.getPluginDescriptor();

        if ( destinationFile.exists() )
        {
            destinationFile.delete();
        }
        else
        {
            if ( !destinationFile.getParentFile().exists() )
            {
                destinationFile.getParentFile().mkdirs();
            }
        }

        String encoding = "UTF-8";

        Writer writer = null;
        try
        {
            writer = new OutputStreamWriter( new FileOutputStream( destinationFile ), encoding );

            XMLWriter w = new PrettyPrintXMLWriter( writer, encoding, null );

            w.writeMarkup( "\n<!-- Generated by maven-plugin-tools " + getVersion() + " on " + new SimpleDateFormat(
                "yyyy-MM-dd" ).format( new Date() ) + " -->\n\n" );

            w.startElement( "plugin" );

            GeneratorUtils.element( w, "name", pluginDescriptor.getName() );

            GeneratorUtils.element( w, "description", pluginDescriptor.getDescription(), helpDescriptor );

            GeneratorUtils.element( w, "groupId", pluginDescriptor.getGroupId() );

            GeneratorUtils.element( w, "artifactId", pluginDescriptor.getArtifactId() );

            GeneratorUtils.element( w, "version", pluginDescriptor.getVersion() );

            GeneratorUtils.element( w, "goalPrefix", pluginDescriptor.getGoalPrefix() );

            if ( !helpDescriptor )
            {
                GeneratorUtils.element( w, "isolatedRealm", String.valueOf( pluginDescriptor.isIsolatedRealm() ) );

                GeneratorUtils.element( w, "inheritedByDefault",
                                        String.valueOf( pluginDescriptor.isInheritedByDefault() ) );
            }

            w.startElement( "mojos" );

            if ( pluginDescriptor.getMojos() != null )
            {
                @SuppressWarnings( "unchecked" ) List<MojoDescriptor> descriptors = pluginDescriptor.getMojos();

                if ( helpDescriptor )
                {
                    PluginUtils.sortMojos( descriptors );
                }

                for ( MojoDescriptor descriptor : descriptors )
                {
                    processMojoDescriptor( descriptor, w, helpDescriptor );
                }
            }

            w.endElement();

            if ( !helpDescriptor )
            {
                GeneratorUtils.writeDependencies( w, pluginDescriptor );
            }

            w.endElement();

            writer.flush();

        }
        finally
        {
            IOUtil.close( writer );
        }
    }

    protected void processMojoDescriptor( MojoDescriptor mojoDescriptor, XMLWriter w )
    {
        processMojoDescriptor( mojoDescriptor, w, false );
    }

    /**
     * @param mojoDescriptor not null
     * @param w              not null
     * @param helpDescriptor will clean html content from description fields
     */
    protected void processMojoDescriptor( MojoDescriptor mojoDescriptor, XMLWriter w, boolean helpDescriptor )
    {
        w.startElement( "mojo" );

        // ----------------------------------------------------------------------
        //
        // ----------------------------------------------------------------------

        w.startElement( "goal" );
        w.writeText( mojoDescriptor.getGoal() );
        w.endElement();

        // ----------------------------------------------------------------------
        //
        // ----------------------------------------------------------------------

        String description = mojoDescriptor.getDescription();

        if ( description != null )
        {
            w.startElement( "description" );
            if ( helpDescriptor )
            {
                w.writeText( GeneratorUtils.toText( mojoDescriptor.getDescription() ) );
            }
            else
            {
                w.writeText( mojoDescriptor.getDescription() );
            }
            w.endElement();
        }

        // ----------------------------------------------------------------------
        //
        // ----------------------------------------------------------------------

        if ( StringUtils.isNotEmpty( mojoDescriptor.isDependencyResolutionRequired() ) )
        {
            GeneratorUtils.element( w, "requiresDependencyResolution",
                                    mojoDescriptor.isDependencyResolutionRequired() );
        }

        // ----------------------------------------------------------------------
        //
        // ----------------------------------------------------------------------

        GeneratorUtils.element( w, "requiresDirectInvocation",
                                String.valueOf( mojoDescriptor.isDirectInvocationOnly() ) );

        // ----------------------------------------------------------------------
        //
        // ----------------------------------------------------------------------

        GeneratorUtils.element( w, "requiresProject", String.valueOf( mojoDescriptor.isProjectRequired() ) );

        // ----------------------------------------------------------------------
        //
        // ----------------------------------------------------------------------

        GeneratorUtils.element( w, "requiresReports", String.valueOf( mojoDescriptor.isRequiresReports() ) );

        // ----------------------------------------------------------------------
        //
        // ----------------------------------------------------------------------

        GeneratorUtils.element( w, "aggregator", String.valueOf( mojoDescriptor.isAggregator() ) );

        // ----------------------------------------------------------------------
        //
        // ----------------------------------------------------------------------

        GeneratorUtils.element( w, "requiresOnline", String.valueOf( mojoDescriptor.isOnlineRequired() ) );

        // ----------------------------------------------------------------------
        //
        // ----------------------------------------------------------------------

        GeneratorUtils.element( w, "inheritedByDefault", String.valueOf( mojoDescriptor.isInheritedByDefault() ) );

        // ----------------------------------------------------------------------
        //
        // ----------------------------------------------------------------------

        if ( StringUtils.isNotEmpty( mojoDescriptor.getPhase() ) )
        {
            GeneratorUtils.element( w, "phase", mojoDescriptor.getPhase() );
        }

        // ----------------------------------------------------------------------
        //
        // ----------------------------------------------------------------------

        if ( StringUtils.isNotEmpty( mojoDescriptor.getExecutePhase() ) )
        {
            GeneratorUtils.element( w, "executePhase", mojoDescriptor.getExecutePhase() );
        }

        if ( StringUtils.isNotEmpty( mojoDescriptor.getExecuteGoal() ) )
        {
            GeneratorUtils.element( w, "executeGoal", mojoDescriptor.getExecuteGoal() );
        }

        if ( StringUtils.isNotEmpty( mojoDescriptor.getExecuteLifecycle() ) )
        {
            GeneratorUtils.element( w, "executeLifecycle", mojoDescriptor.getExecuteLifecycle() );
        }

        // ----------------------------------------------------------------------
        //
        // ----------------------------------------------------------------------

        w.startElement( "implementation" );
        w.writeText( mojoDescriptor.getImplementation() );
        w.endElement();

        // ----------------------------------------------------------------------
        //
        // ----------------------------------------------------------------------

        w.startElement( "language" );
        w.writeText( mojoDescriptor.getLanguage() );
        w.endElement();

        // ----------------------------------------------------------------------
        //
        // ----------------------------------------------------------------------

        if ( StringUtils.isNotEmpty( mojoDescriptor.getComponentConfigurator() ) )
        {
            w.startElement( "configurator" );
            w.writeText( mojoDescriptor.getComponentConfigurator() );
            w.endElement();
        }

        // ----------------------------------------------------------------------
        //
        // ----------------------------------------------------------------------

        if ( StringUtils.isNotEmpty( mojoDescriptor.getComponentComposer() ) )
        {
            w.startElement( "composer" );
            w.writeText( mojoDescriptor.getComponentComposer() );
            w.endElement();
        }

        // ----------------------------------------------------------------------
        //
        // ----------------------------------------------------------------------

        w.startElement( "instantiationStrategy" );
        w.writeText( mojoDescriptor.getInstantiationStrategy() );
        w.endElement();

        // ----------------------------------------------------------------------
        // Strategy for handling repeated reference to mojo in
        // the calculated (decorated, resolved) execution stack
        // ----------------------------------------------------------------------
        w.startElement( "executionStrategy" );
        w.writeText( mojoDescriptor.getExecutionStrategy() );
        w.endElement();

        // ----------------------------------------------------------------------
        //
        // ----------------------------------------------------------------------

        if ( mojoDescriptor.getSince() != null )
        {
            w.startElement( "since" );

            if ( StringUtils.isEmpty( mojoDescriptor.getSince() ) )
            {
                w.writeText( "No version given" );
            }
            else
            {
                w.writeText( mojoDescriptor.getSince() );
            }

            w.endElement();
        }

        // ----------------------------------------------------------------------
        //
        // ----------------------------------------------------------------------

        if ( mojoDescriptor.getDeprecated() != null )
        {
            w.startElement( "deprecated" );

            if ( StringUtils.isEmpty( mojoDescriptor.getDeprecated() ) )
            {
                w.writeText( "No reason given" );
            }
            else
            {
                w.writeText( mojoDescriptor.getDeprecated() );
            }

            w.endElement();
        }

        // ----------------------------------------------------------------------
        // Extended (3.0) descriptor
        // ----------------------------------------------------------------------

        if ( mojoDescriptor instanceof ExtendedMojoDescriptor )
        {
            ExtendedMojoDescriptor extendedMojoDescriptor = (ExtendedMojoDescriptor) mojoDescriptor;
            if ( extendedMojoDescriptor.getDependencyCollectionRequired() != null )
            {
                GeneratorUtils.element( w, "requiresDependencyCollection",
                                        extendedMojoDescriptor.getDependencyCollectionRequired() );
            }

            GeneratorUtils.element( w, "threadSafe", String.valueOf( extendedMojoDescriptor.isThreadSafe() ) );
        }

        // ----------------------------------------------------------------------
        // Parameters
        // ----------------------------------------------------------------------

        @SuppressWarnings( "unchecked" ) List<Parameter> parameters = mojoDescriptor.getParameters();

        w.startElement( "parameters" );

        Map<String, Requirement> requirements = new LinkedHashMap<String, Requirement>();

        Set<Parameter> configuration = new LinkedHashSet<Parameter>();

        if ( parameters != null )
        {
            if ( helpDescriptor )
            {
                PluginUtils.sortMojoParameters( parameters );
            }

            for ( Parameter parameter : parameters )
            {
                String expression = getExpression( parameter );

                if ( StringUtils.isNotEmpty( expression ) && expression.startsWith( "${component." ) )
                {
                    // treat it as a component...a requirement, in other words.

                    // remove "component." plus expression delimiters
                    String role = expression.substring( "${component.".length(), expression.length() - 1 );

                    String roleHint = null;

                    int posRoleHintSeparator = role.indexOf( "#" );
                    if ( posRoleHintSeparator > 0 )
                    {
                        roleHint = role.substring( posRoleHintSeparator + 1 );

                        role = role.substring( 0, posRoleHintSeparator );
                    }

                    // TODO: remove deprecated expression
                    requirements.put( parameter.getName(), new Requirement( role, roleHint ) );
                }
                else if ( parameter.getRequirement() != null )
                {
                    requirements.put( parameter.getName(), parameter.getRequirement() );
                }
                else if ( !helpDescriptor || parameter.isEditable() ) // don't show readonly parameters in help
                {
                    // treat it as a normal parameter.

                    w.startElement( "parameter" );

                    GeneratorUtils.element( w, "name", parameter.getName() );

                    if ( parameter.getAlias() != null )
                    {
                        GeneratorUtils.element( w, "alias", parameter.getAlias() );
                    }

                    GeneratorUtils.element( w, "type", parameter.getType() );

                    if ( parameter.getSince() != null )
                    {
                        w.startElement( "since" );

                        if ( StringUtils.isEmpty( parameter.getSince() ) )
                        {
                            w.writeText( "No version given" );
                        }
                        else
                        {
                            w.writeText( parameter.getSince() );
                        }

                        w.endElement();
                    }

                    if ( parameter.getDeprecated() != null )
                    {
                        if ( StringUtils.isEmpty( parameter.getDeprecated() ) )
                        {
                            GeneratorUtils.element( w, "deprecated", "No reason given" );
                        }
                        else
                        {
                            GeneratorUtils.element( w, "deprecated", parameter.getDeprecated() );
                        }
                    }

                    if ( parameter.getImplementation() != null )
                    {
                        GeneratorUtils.element( w, "implementation", parameter.getImplementation() );
                    }

                    GeneratorUtils.element( w, "required", Boolean.toString( parameter.isRequired() ) );

                    GeneratorUtils.element( w, "editable", Boolean.toString( parameter.isEditable() ) );

                    GeneratorUtils.element( w, "description", parameter.getDescription(), helpDescriptor );

                    if ( StringUtils.isNotEmpty( parameter.getDefaultValue() ) || StringUtils.isNotEmpty(
                        parameter.getExpression() ) )
                    {
                        configuration.add( parameter );
                    }

                    w.endElement();
                }

            }
        }

        w.endElement();

        // ----------------------------------------------------------------------
        // Configuration
        // ----------------------------------------------------------------------

        if ( !configuration.isEmpty() )
        {
            w.startElement( "configuration" );

            for ( Parameter parameter : configuration )
            {
                if ( helpDescriptor && !parameter.isEditable() )
                {
                    // don't show readonly parameters in help
                    continue;
                }

                w.startElement( parameter.getName() );

                String type = parameter.getType();
                if ( StringUtils.isNotEmpty( type ) )
                {
                    w.addAttribute( "implementation", type );
                }

                if ( parameter.getDefaultValue() != null )
                {
                    w.addAttribute( "default-value", parameter.getDefaultValue() );
                }

                if ( StringUtils.isNotEmpty( parameter.getExpression() ) )
                {
                    w.writeText( parameter.getExpression() );
                }

                w.endElement();
            }

            w.endElement();
        }

        // ----------------------------------------------------------------------
        // Requirements
        // ----------------------------------------------------------------------

        if ( !requirements.isEmpty() && !helpDescriptor )
        {
            w.startElement( "requirements" );

            for ( Map.Entry<String, Requirement> entry : requirements.entrySet() )
            {
                String key = entry.getKey();
                Requirement requirement = entry.getValue();

                w.startElement( "requirement" );

                GeneratorUtils.element( w, "role", requirement.getRole() );

                if ( StringUtils.isNotEmpty( requirement.getRoleHint() ) )
                {
                    GeneratorUtils.element( w, "role-hint", requirement.getRoleHint() );
                }

                GeneratorUtils.element( w, "field-name", key );

                w.endElement();
            }

            w.endElement();
        }

        w.endElement();
    }

    /**
     * Get the expression value, eventually surrounding it with <code>${ }</code>.
     *
     * @param parameter the parameter
     * @return the expression value
     */
    private String getExpression( Parameter parameter )
    {
        String expression = parameter.getExpression();
        if ( StringUtils.isNotBlank( expression ) && !expression.contains( "${" ) )
        {
            expression = "${" + expression.trim() + "}";
            parameter.setExpression( expression );
        }
        return expression;
    }
}
