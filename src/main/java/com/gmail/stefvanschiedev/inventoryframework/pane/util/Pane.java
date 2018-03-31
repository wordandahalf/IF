package com.gmail.stefvanschiedev.inventoryframework.pane.util;

import com.gmail.stefvanschiedev.inventoryframework.GuiItem;
import com.gmail.stefvanschiedev.inventoryframework.GuiLocation;
import com.google.common.primitives.Primitives;
import org.bukkit.Material;
import org.bukkit.event.Cancellable;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The base class for all panes.
 */
public abstract class Pane {

    /**
     * The starting position of this pane
     */
    protected GuiLocation start;

    /**
     * Length is horizontal, height is vertical
     */
    protected int length, height;

    /**
     * The visibility state of the pane
     */
    private boolean visible;

    /**
     * The tag assigned to this pane, null if no tag has been assigned
     */
    private String tag;

    /**
     * The consumer that will be called once a players clicks in the gui
     */
    protected Consumer<InventoryClickEvent> onClick;

    /**
     * A map containing the mappings for properties for items
     */
    private static final Map<String, Function<String, Object>> PROPERTY_MAPPINGS = new HashMap<>();

    /**
     * Constructs a new default pane
     *
     * @param start the upper left corner of the pane
     * @param length the length of the pane
     * @param height the height of the pane
     */
    protected Pane(@NotNull GuiLocation start, int length, int height) {
        assert start.getX() + length <= 9 : "length longer than maximum size";
        assert start.getY() + height <= 6 : "height longer than maximum size";

        this.start = start;

        this.length = length;
        this.height = height;

        this.visible = true;
    }

    /**
     * Returns the length of this pane
     *
     * @return the length
     */
    public int getLength() {
        return length;
    }

    /**
     * Returns the height of this pane
     *
     * @return the height
     */
    public int getHeight() {
        return height;
    }

    /**
     * Has to set all the items in the right spot inside the inventory
     *
     * @param inventory the inventory that the items should be displayed in
     */
    public abstract void display(Inventory inventory);

    /**
     * Returns the pane's visibility state
     *
     * @return the pane's visibility
     */
    @Contract(pure = true)
    public boolean isVisible() {
        return visible;
    }

    /**
     * Sets whether this pane is visible or not
     *
     * @param visible the pane's visibility
     */
    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    /**
     * Called whenever there is being clicked on this pane
     *
     * @param event the event that occurred while clicking on this item
     * @return whether the item was found or not
     */
    public abstract boolean click(@NotNull InventoryClickEvent event);

    /**
     * Returns a gui item by tag
     *
     * @param tag the tag to look for
     * @return the gui item
     */
    @Nullable
    public abstract GuiItem getItem(@NotNull String tag);

    /**
     * Returns the tag that belongs to this item, or null if no tag has been assigned
     *
     * @return the tag or null
     */
    @Nullable
    @Contract(pure = true)
    public String getTag() {
        return tag;
    }

    /**
     * Sets the tag of this item to the new tag or removes it when the parameter is null
     *
     * @param tag the new tag
     */
    public void setTag(@Nullable String tag) {
        this.tag = tag;
    }

    /**
     * Set the consumer that should be called whenever this gui is clicked in.
     *
     * @param onClick the consumer that gets called
     */
    public void setOnClick(Consumer<InventoryClickEvent> onClick) {
        this.onClick = onClick;
    }

    /**
     * Loads an item from an instance and an element
     *
     * @param instance the instance
     * @param element the element
     * @return the gui item
     */
    public static GuiItem loadItem(Object instance, Element element) {
        String id = element.getAttribute("id");
        ItemStack itemStack = new ItemStack(Material.matchMaterial(id.toUpperCase(Locale.getDefault())), 1,
                element.hasAttribute("damage") ? Short.parseShort(element.getAttribute("damage")) : 0);

        if (element.hasAttribute("displayName")) {
            ItemMeta itemMeta = itemStack.getItemMeta();

            itemMeta.setDisplayName(element.getAttribute("displayName"));
            itemStack.setItemMeta(itemMeta);
        }

        if (element.hasAttribute("lores")) {
            ItemMeta itemMeta = itemStack.getItemMeta();

            itemStack.setItemMeta(itemMeta);
        }

        String tag = null;

        if (element.hasAttribute("tag"))
            tag = element.getAttribute("tag");

        List<Object> properties = new ArrayList<>();

        if (element.hasChildNodes()) {
            NodeList childNodes = element.getChildNodes();

            for (int i = 0; i < childNodes.getLength(); i++) {
                Node item = childNodes.item(i);

                if (item.getNodeType() != Node.ELEMENT_NODE || !item.getNodeName().equals("properties"))
                    continue;

                Element propertyList = (Element) item;

                for (int j = 0; j < propertyList.getChildNodes().getLength(); j++) {
                    Node propertyNode = propertyList.getChildNodes().item(j);

                    if (propertyNode.getNodeType() != Node.ELEMENT_NODE ||
                            !propertyNode.getNodeName().equals("property"))
                        continue;

                    Element property = (Element) propertyNode;
                    String propertyType;

                    if (!property.hasAttribute("type"))
                        propertyType = "string";
                    else
                        propertyType = property.getAttribute("type");

                    properties.add(PROPERTY_MAPPINGS.get(propertyType).apply(property.getTextContent()));
                }
            }
        }

        Consumer<InventoryClickEvent> action = null;

        if (element.hasAttribute("onClick")) {
            for (Method method : instance.getClass().getMethods()) {
                if (!method.getName().equals(element.getAttribute("onClick")))
                    continue;

                int parameterCount = method.getParameterCount();
                Class<?>[] parameterTypes = method.getParameterTypes();

                if (parameterCount == 0)
                    action = event -> {
                        try {
                            //because reflection with lambdas is stupid
                            method.setAccessible(true);
                            method.invoke(instance);
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            e.printStackTrace();
                        }
                    };
                else if (InventoryClickEvent.class.isAssignableFrom(parameterTypes[0]) ||
                    Cancellable.class.isAssignableFrom(parameterTypes[0])) {
                    if (parameterCount == 1)
                        action = event -> {
                            try {
                                //because reflection with lambdas is stupid
                                method.setAccessible(true);
                                method.invoke(instance, event);
                            } catch (IllegalAccessException | InvocationTargetException e) {
                                e.printStackTrace();
                            }
                        };
                    else if (parameterCount == properties.size() + 1) {
                        boolean correct = true;

                        for (int i = 0; i < properties.size(); i++) {
                            Object attribute = properties.get(i);

                            if (!(parameterTypes[1 + i].isPrimitive() &&
                                    Primitives.unwrap(attribute.getClass()).isAssignableFrom(parameterTypes[1 + i])) &&
                                    !attribute.getClass().isAssignableFrom(parameterTypes[1 + i]))
                                correct = false;
                        }

                        if (correct) {
                            action = event -> {
                                try {
                                    //don't ask me why we need to do this, just roll with it (actually I do know why, but it's stupid)
                                    properties.add(0, event);

                                    //because reflection with lambdas is stupid
                                    method.setAccessible(true);
                                    method.invoke(instance, properties.toArray(new Object[0]));
                                } catch (IllegalAccessException | InvocationTargetException e) {
                                    e.printStackTrace();
                                }
                            };
                        }
                    }
                }

                break;
            }
        }

        GuiItem item = action == null ? new GuiItem(itemStack) : new GuiItem(itemStack, action);
        item.setTag(tag);

        return item;
    }

    /**
     * Returns the property mappings used when loading properties from an XML file.
     *
     * @return the property mappings
     */
    @NotNull
    @Contract(pure = true)
    public static Map<String, Function<String, Object>> getPropertyMappings() {
        return PROPERTY_MAPPINGS;
    }

    static {
        PROPERTY_MAPPINGS.put("boolean", Boolean::parseBoolean);
        PROPERTY_MAPPINGS.put("byte", Byte::parseByte);
        PROPERTY_MAPPINGS.put("character", value -> value.charAt(0));
        PROPERTY_MAPPINGS.put("double", Double::parseDouble);
        PROPERTY_MAPPINGS.put("float", Float::parseFloat);
        PROPERTY_MAPPINGS.put("integer", Integer::parseInt);
        PROPERTY_MAPPINGS.put("long", Long::parseLong);
        PROPERTY_MAPPINGS.put("short", Short::parseShort);
        PROPERTY_MAPPINGS.put("string", value -> value);
    }
}