package com.structurizr.dsl;

import com.structurizr.model.Element;
import com.structurizr.model.ModelItem;
import com.structurizr.model.Relationship;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import static com.structurizr.dsl.StructurizrDslExpressions.*;

abstract class AbstractExpressionParser {

    private static final String WILDCARD = "*";

    final Set<ModelItem> parseExpression(String expr, DslContext context) {
        if (expr.contains(" && ")) {
            String[] expressions = expr.split(" && ");
            Set<ModelItem> modelItems1 = evaluateExpression(expressions[0], context);
            Set<ModelItem> modelItems2 = evaluateExpression(expressions[1], context);

            Set<ModelItem> modelItems = new HashSet<>(modelItems1);
            modelItems.retainAll(modelItems2);

            return modelItems;
        } else if (expr.contains(" || ")) {
            String[] expressions = expr.split(" \\|\\| ");
            Set<ModelItem> modelItems1 = evaluateExpression(expressions[0], context);
            Set<ModelItem> modelItems2 = evaluateExpression(expressions[1], context);

            Set<ModelItem> elements = new HashSet<>(modelItems1);
            elements.addAll(modelItems2);

            return elements;
        } else {
            return evaluateExpression(expr, context);
        }
    }

    private Set<ModelItem> evaluateExpression(String expr, DslContext context) {
        Set<ModelItem> modelItems = new LinkedHashSet<>();

        if (expr.toLowerCase().startsWith(ELEMENT_TYPE_EQUALS_EXPRESSION)) {
            modelItems.addAll(evaluateElementTypeExpression(expr, context));
        } else if (expr.toLowerCase().startsWith(ELEMENT_TAG_EQUALS_EXPRESSION.toLowerCase())) {
            String[] tags = expr.substring(ELEMENT_TAG_EQUALS_EXPRESSION.length()).split(",");
            context.getWorkspace().getModel().getElements().forEach(element -> {
                if (hasAllTags(element, tags)) {
                    modelItems.add(element);
                }
            });
        } else if (expr.toLowerCase().startsWith(ELEMENT_TAG_NOT_EQUALS_EXPRESSION)) {
            String[] tags = expr.substring(ELEMENT_TAG_NOT_EQUALS_EXPRESSION.length()).split(",");
            context.getWorkspace().getModel().getElements().forEach(element -> {
                if (!hasAllTags(element, tags)) {
                    modelItems.add(element);
                }
            });
        } else if (expr.startsWith(RELATIONSHIP_TAG_EQUALS_EXPRESSION)) {
            String[] tags = expr.substring(RELATIONSHIP_TAG_EQUALS_EXPRESSION.length()).split(",");
            context.getWorkspace().getModel().getRelationships().forEach(relationship -> {
                if (hasAllTags(relationship, tags)) {
                    modelItems.add(relationship);
                }
            });
        } else if (expr.startsWith(RELATIONSHIP_TAG_NOT_EQUALS_EXPRESSION)) {
            String[] tags = expr.substring(RELATIONSHIP_TAG_NOT_EQUALS_EXPRESSION.length()).split(",");
            context.getWorkspace().getModel().getRelationships().forEach(relationship -> {
                if (!hasAllTags(relationship, tags)) {
                    modelItems.add(relationship);
                }
            });
        } else if (expr.startsWith(RELATIONSHIP_SOURCE_EQUALS_EXPRESSION)) {
            String identifier = expr.substring(RELATIONSHIP_SOURCE_EQUALS_EXPRESSION.length());
            Element source = context.getElement(identifier);

            if (source == null) {
                throw new RuntimeException("The element \"" + identifier + "\" does not exist");
            }

            Set<Element> sourceElements = new HashSet<>();
            if (source instanceof ElementGroup) {
                sourceElements.addAll(((ElementGroup)source).getElements());
            } else {
                sourceElements.add(source);
            }

            context.getWorkspace().getModel().getRelationships().forEach(relationship -> {
                if (sourceElements.contains(relationship.getSource())) {
                    modelItems.add(relationship);
                }
            });
        } else if (expr.startsWith(RELATIONSHIP_DESTINATION_EQUALS_EXPRESSION)) {
            String identifier = expr.substring(RELATIONSHIP_DESTINATION_EQUALS_EXPRESSION.length());
            Element destination = context.getElement(identifier);

            if (destination == null) {
                throw new RuntimeException("The element \"" + identifier + "\" does not exist");
            }

            Set<Element> destinationElements = new HashSet<>();
            if (destination instanceof ElementGroup) {
                destinationElements.addAll(((ElementGroup) destination).getElements());
            } else {
                destinationElements.add(destination);
            }

            context.getWorkspace().getModel().getRelationships().forEach(relationship -> {
                if (destinationElements.contains(relationship.getDestination())) {
                    modelItems.add(relationship);
                }
            });
        } else {
            if (expr.startsWith(ELEMENT_EQUALS_EXPRESSION)) {
                expr = expr.substring(ELEMENT_EQUALS_EXPRESSION.length());
            }

            if (expr.startsWith(RELATIONSHIP_EQUALS_EXPRESSION)) {
                expr = expr.substring(RELATIONSHIP_EQUALS_EXPRESSION.length());

                if (WILDCARD.equals(expr)) {
                    expr = WILDCARD + RELATIONSHIP + WILDCARD;
                }
            }

            if (RELATIONSHIP.equals(expr)) {
                throw new RuntimeException("Unexpected identifier \"->\"");
            } else {
                modelItems.addAll(parseIdentifierExpression(expr, context));
            }
        }

        return modelItems;
    }

    protected abstract Set<Element> evaluateElementTypeExpression(String expr, DslContext context);

    private boolean hasAllTags(ModelItem modelItem, String[] tags) {
        boolean result = true;

        for (String tag : tags) {
            result = result && modelItem.hasTag(tag.trim());
        }

        return result;
    }

    protected abstract Set<Element> findAfferentCouplings(Element element);

    protected <T extends Element> Set<Element> findAfferentCouplings(Element element, Class<T> typeOfElement) {
        Set<Element> elements = new LinkedHashSet<>();

        Set<Relationship> relationships = element.getModel().getRelationships();
        relationships.stream().filter(r -> r.getDestination().equals(element) && typeOfElement.isInstance(r.getSource()))
                .map(Relationship::getSource)
                .forEach(elements::add);

        return elements;
    }

    protected abstract Set<Element> findEfferentCouplings(Element element);

    protected <T extends Element> Set<Element> findEfferentCouplings(Element element, Class<T> typeOfElement) {
        Set<Element> elements = new LinkedHashSet<>();

        Set<Relationship> relationships = element.getModel().getRelationships();
        relationships.stream().filter(r -> r.getSource().equals(element) && typeOfElement.isInstance(r.getDestination()))
                .map(Relationship::getDestination)
                .forEach(elements::add);

        return elements;
    }

    protected Set<ModelItem> parseIdentifierExpression(String expr, DslContext context) {
        Set<ModelItem> modelItems = new LinkedHashSet<>();

        // simplest case: this is an element or relationship identifier
        if (!expr.contains(RELATIONSHIP)) {
            Element element = context.getElement(expr);
            if (element != null) {
                modelItems.addAll(getElements(expr, context));
            }

            Relationship relationship = context.getRelationship(expr);
            if (relationship != null) {
                modelItems.add(relationship);
            }

            if (modelItems.isEmpty()) {
                throw new RuntimeException("The element/relationship \"" + expr + "\" does not exist");
            } else {
                return modelItems;
            }
        } else if (expr.startsWith(RELATIONSHIP) || expr.endsWith(RELATIONSHIP)) {
            // this is an element expression: ->identifier identifier-> ->identifier->
            boolean includeAfferentCouplings = false;
            boolean includeEfferentCouplings = false;

            String identifier = expr;

            if (identifier.startsWith(RELATIONSHIP)) {
                includeAfferentCouplings = true;
                identifier = identifier.substring(RELATIONSHIP.length());
            }
            if (identifier.endsWith(RELATIONSHIP)) {
                includeEfferentCouplings = true;
                identifier = identifier.substring(0, identifier.length() - RELATIONSHIP.length());
            }

            identifier = identifier.trim();
            Set<Element> elements = getElements(identifier, context);

            if (elements.isEmpty()) {
                throw new RuntimeException("The element \"" + identifier + "\" does not exist");
            }

            for (Element element : elements) {
                modelItems.add(element);

                if (includeAfferentCouplings) {
                    modelItems.addAll(findAfferentCouplings(element));
                }

                if (includeEfferentCouplings) {
                    modelItems.addAll(findEfferentCouplings(element));
                }
            }
        } else if (expr.contains(RELATIONSHIP)) {
            String[] identifiers = expr.split(RELATIONSHIP);
            String sourceIdentifier = identifiers[0].trim();
            String destinationIdentifier = identifiers[1].trim();

            String sourceExpression = RELATIONSHIP_SOURCE_EQUALS_EXPRESSION + sourceIdentifier;
            String destinationExpression = RELATIONSHIP_DESTINATION_EQUALS_EXPRESSION + destinationIdentifier;

            if (WILDCARD.equals(sourceIdentifier) && WILDCARD.equals(destinationIdentifier)) {
                modelItems.addAll(context.getWorkspace().getModel().getRelationships());
            } else if (WILDCARD.equals(destinationIdentifier)) {
                modelItems.addAll(parseExpression(sourceExpression, context));
            } else if (WILDCARD.equals(sourceIdentifier)) {
                modelItems.addAll(parseExpression(destinationExpression, context));
            } else {
                modelItems.addAll(parseExpression(sourceExpression + " && " + destinationExpression, context));
            }
        }


        return modelItems;
    }

    private Set<Element> getElements(String identifier, DslContext context) {
        Set<Element> elements = new HashSet<>();

        Element element = context.getElement(identifier);
        if (element instanceof ElementGroup) {
            ElementGroup group = (ElementGroup)element;
            elements.addAll(group.getElements());
        } else {
            elements.add(element);
        }

        return elements;
    }

}