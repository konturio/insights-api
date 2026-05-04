# Stat JSON Spec

## Root
[Type Stat](#stat)

 - [Axis property](#axis-property)
 - [Correlation Rate property](#correlation-rate-property)
 - [Meta property](#meta-property)
 - [InitAxis property](#initaxis-property)
 - [Overlays property](#overlays-property)
 - [Copyrights property](#copyrights-property)
 - [Translation property](#translation-property)
 - [Types](#types)



## Axis property
> Need to fix - rename it to axes  
> Need to fix - add id property (currently label using as id)

[Type Axis](#axis)

Contain all axes what can be constructed in app with their [divisor and denominator pairs](#quotient),
where divisor and denominator - some available datasets (literally - fields in tiles data);
For every pair described min, average and max value in `steps` property.

Also in contains `label` - that answer how to axis named, and it currently used as id.

This is necessary in order to correctly define the "color borders" for map (maping values to colors)
and sign a legend. If a [label is specified](#step) in addition to the values, it will be used in the legend instead of a number.

Why it must be pre-calculated? - frontend can't calculate this values in runtime because data baked in tiles which will be uploaded on demand.

## Correlation Rate property
[Type CorrelationRate](#correlationrate)

Rate property shows us how interest data contains in the axis pair.

## Meta property

[Type Meta](#meta)

Tell that tiles can be requested from `min_zoom` to `max_zoom`. If the map zoom is outside these limits information from the nearest border will be used. For example if `max_zoom: 8`, and user zoom in to `10`, client request tiles for `zoom === 8` and scale it.

## InitAxis property

[Type InitAxis](#initaxis)

This property used for set default legend settings, another words - what user to see when app loaded.
So yes - this is just two entries from [axis property](#axis) - for `x` axis and for `y` axis;

## Overlays property

[Type Overlay](#overlay)

Currently used in disaster ninja for define available for selection set of settings.
Very similar to InitAxis property but with few extra properties:
 - active (boolean) - describe should be active or not be default
 - name - how to display that overlay in ui controls
 - description - put that text near legend
 - colors - legend colors. This color also will be used for colorize layer data on map

## Copyrights property

[Type Copyrights](#copyrights)

Copyrights that should be displayed for the dataset.

Shape:
```
some_dataset: [paragraph_1, paragraph_2, ...paragraph_n],
some_another_dataset: [paragraph_1, paragraph_2, ...paragraph_n]
```

## Translation property

[Type Translation](#translations)

> Need to fix - how to choice language?

Currently client just take label and replace it by value from this dict, using label as key.

## Types

### Color

```ts
type Color = {
  id: string;   // A1 - C3 
  color: string; // rgb(0,0,0) - rgb(255,255,255)
}
```

### Step

```ts
type Step = {
  label?: string;
  value: number;
}
```

### Quotient
Divisor and denominator pair

```ts
type Quotient = [string, string];
```

### Axis
[Axis property](#axis-property)

```ts
type Axis = {
  label: string;
  steps: Step[];
  quotient: Quotient;
}
```

### CorrelationRate
[Correlation Rate property](#correlation-rate-property)

```ts
type CorrelationRate = {
  x: Axis;
  y: Axis;
  rate: number;
}
```
### InitAxis
[InitAxis property](#initaxis-property)


```ts
type InitAxis = {
  x: Axis;
  y: Axis;
}
```

### Overlay
[Overlays property](#overlays-property)

```ts
type Overlay = {
  name: string;
  description: string;
  active: boolean;
  color: Color[];
  x: Axis;
  y: Axis;
}
```

### Meta
[Meta property](#meta-property)

```ts
type Meta = {
  min_zoom: number;
  max_zoom: number;
}
```

### Copyrights
[Copyrights property](#copyrights-property)


```ts
type Copyrights = {
  [key: string]: string[];
}
```

### Translations
[Translation property](#translation-property)

```ts
type Translations = {
  [key: string]: string;
}
```

### Stat
```ts
interface Stat {
  axis: Axis[];
  meta: Meta;
  initAxis: InitAxis;
  correlationRates: CorrelationRate[];
  overlays: Overlay[];
  copyrights: Copyrights;
  translations: Translations;
}
```

