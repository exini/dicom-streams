# Dicom Streams RELEASE NOTES

## 5.1.0

- `toUtf8Flow` now respects SpecificCharacterSet attributes in sequence items. Values in an item are decoded using
  the item's own character set, or the character set of the enclosing dataset if the item does not specify one.
  Previously, only the top-level SpecificCharacterSet was used.
- `toUtf8Flow` now removes SpecificCharacterSet attributes from sequence items, so that items inherit ISO_IR 192 from
  the top-level dataset
- Fixed bug where `toUtf8Flow` could produce odd-length values

## Release 5.0.4

- Fixed bug where switching to indeterminate length sequences and items in big endian files led to inserted 
  delimitations with the wrong endianess (little endian)
